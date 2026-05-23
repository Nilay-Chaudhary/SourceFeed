package com.socialfeed.service;

import com.socialfeed.dto.CreatePostRequest;
import com.socialfeed.dto.LikeStatusDto;
import com.socialfeed.dto.PostDto;
import com.socialfeed.event.NotificationEvent;
import com.socialfeed.event.PostCreatedEvent;
import com.socialfeed.model.AiVerdict;
import com.socialfeed.model.Notification;
import com.socialfeed.model.PostDislike;
import com.socialfeed.model.Post;
import com.socialfeed.model.PostLike;
import com.socialfeed.model.User;
import com.socialfeed.repository.PostDislikeRepository;
import com.socialfeed.repository.PostLikeRepository;
import com.socialfeed.repository.PostRepository;
import com.socialfeed.repository.FollowRepository;
import com.socialfeed.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private static final Set<String> ALLOWED_TOPIC_TAGS = Set.of(
        "physics",
        "chemistry",
        "biology",
        "finance",
        "computer-science"
    );

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostDislikeRepository postDislikeRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final TrustAggregateService trustAggregateService;
    private final TopicReputationService topicReputationService;
    private final KafkaTemplate<String, PostCreatedEvent> kafkaTemplate;
    private final KafkaTemplate<String, NotificationEvent> notificationKafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.topics.post-created}")
    private String postCreatedTopic;

    @Value("${kafka.topics.notifications:notifications}")
    private String notificationsTopic;

    @Value("${app.fanout.celebrity-threshold:10000}")
    private int celebrityThreshold;

    @Value("${app.posts.trusted-source-domains:}")
    private String trustedSourceDomains;

    @Value("${app.posts.trusted-source-boost-per-match:0.25}")
    private double trustedSourceBoostPerMatch;

    @Value("${app.posts.max-source-trust-boost:0.75}")
    private double maxSourceTrustBoost;

    @Value("${app.posts.creator-domain-trust-weight:0.25}")
    private double creatorDomainTrustWeight;

    @Value("${app.posts.max-creator-domain-boost:0.60}")
    private double maxCreatorDomainBoost;

    @Transactional
    public PostDto createPost(Long userId, CreatePostRequest request) {
        long startNanos = System.nanoTime();
        log.info("Create post requested: userId={}, contentLength={}", userId, request.getContent() == null ? 0 : request.getContent().length());

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = new Post();
        post.setUser(user);
        post.setParentPostId(null);
        post.setContent(request.getContent());

        List<String> sources = sanitizeAndValidateSources(request.getSources());
        List<String> topicTags = sanitizeAndValidateTopicTags(request.getTopicTags());
        post.setSources(new ArrayList<>(sources));
        post.setTopicTags(new ArrayList<>(topicTags));

        double sourceBoost = computeSourceTrustBoost(sources);
        double creatorGlobalTrust = user.getUserTrustScore() == null ? 1.0d : Math.max(0.0d, user.getUserTrustScore());
        double creatorDomainTrust = topicReputationService.resolveTrustForTopics(userId, topicTags, creatorGlobalTrust);
        double creatorDomainBoost = computeCreatorDomainTrustBoost(creatorDomainTrust);
        post.setPostTrustScore(sourceBoost + creatorDomainBoost);

        post = postRepository.save(post);

        // Determine whether this author is a celebrity (high follower count)
        long followerCount = followRepository.countByFolloweeId(user.getId());
        if (followerCount >= celebrityThreshold) {
            post.setCelebrityPost(true);
            post = postRepository.save(post);
            log.info("Marked post as celebrity (skip fanout): postId={}, authorId={}, followerCount={}", post.getId(), user.getId(), followerCount);
        }

        trustAggregateService.recordPostCreated(user.getId(), post.getPostTrustScore() == null ? 0.0d : post.getPostTrustScore());

        // Publish event to Kafka
        PostCreatedEvent event = new PostCreatedEvent(
            post.getId(),
            post.getParentPostId(),
            user.getId(),
            post.getContent(),
            post.getCreatedAt(),
            UUID.randomUUID().toString(), // Event ID for idempotency
            post.isCelebrityPost()
        );

        String messageKey = user.getId().toString();
        log.info(
            "Publishing post created event: eventId={}, postId={}, userId={}, topic={}, key={}",
            event.getEventId(), event.getPostId(), event.getUserId(), postCreatedTopic, messageKey
        );

        publishPostCreatedEventAfterCommit(messageKey, event);

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("Post persisted and event dispatch initiated: postId={}, userId={}, eventId={}, durationMs={}", post.getId(), user.getId(), event.getEventId(), durationMs);

        return toDto(post);
    }

    @Transactional
    public PostDto createCounterpoint(Long parentPostId, Long userId, CreatePostRequest request) {
        long startNanos = System.nanoTime();
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new RuntimeException("Counterpoint content is required");
        }
        if (content.length() > 300) {
            throw new RuntimeException("Counterpoint content must be 300 characters or less");
        }

        Post parentPost = postRepository.findById(parentPostId)
            .orElseThrow(() -> new RuntimeException("Parent post not found"));

        if (parentPost.getParentPostId() != null) {
            throw new RuntimeException("Counterpoints can only be added to root posts");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Post counterpoint = new Post();
        counterpoint.setUser(user);
        counterpoint.setParentPostId(parentPostId);
        counterpoint.setContent(content);

        List<String> sources = sanitizeAndValidateSources(request.getSources());
        List<String> topicTags = parentPost.getTopicTags() == null ? List.of() : new ArrayList<>(parentPost.getTopicTags());
        counterpoint.setSources(new ArrayList<>(sources));
        counterpoint.setTopicTags(topicTags);

        double sourceBoost = computeSourceTrustBoost(sources);
        double creatorGlobalTrust = user.getUserTrustScore() == null ? 1.0d : Math.max(0.0d, user.getUserTrustScore());
        double creatorDomainTrust = topicReputationService.resolveTrustForTopics(userId, topicTags, creatorGlobalTrust);
        double creatorDomainBoost = computeCreatorDomainTrustBoost(creatorDomainTrust);
        counterpoint.setPostTrustScore(sourceBoost + creatorDomainBoost);

        counterpoint = postRepository.save(counterpoint);

        trustAggregateService.recordPostCreated(user.getId(), counterpoint.getPostTrustScore() == null ? 0.0d : counterpoint.getPostTrustScore());

        PostCreatedEvent event = new PostCreatedEvent(
            counterpoint.getId(),
            counterpoint.getParentPostId(),
            user.getId(),
            counterpoint.getContent(),
            counterpoint.getCreatedAt(),
            UUID.randomUUID().toString(),
            false
        );

        publishPostCreatedEventAfterCommit(user.getId().toString(), event);
        
        // Publish notification to parent post creator
        NotificationEvent notificationEvent = NotificationEvent.builder()
            .userId(parentPost.getUser().getId())
            .type(Notification.NotificationType.COUNTERPOINT_CREATED)
            .message(user.getUsername() + " added a comment to your post")
            .relatedPostId(parentPostId)
            .relatedUserId(userId)
            .timestamp(java.time.Instant.now())
            .build();
        
        publishNotificationEventAfterCommit(parentPost.getUser().getId().toString(), notificationEvent);

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("Counterpoint persisted and event dispatch initiated: postId={}, parentPostId={}, userId={}, eventId={}, durationMs={}", counterpoint.getId(), parentPostId, user.getId(), event.getEventId(), durationMs);

        return toDto(counterpoint);
    }

    private void publishPostCreatedEventAfterCommit(String messageKey, PostCreatedEvent event) {
        Runnable publish = () -> kafkaTemplate.send(postCreatedTopic, messageKey, event)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    meterRegistry.counter("posts_created_total", "status", "success").increment();
                    if (result != null && result.getRecordMetadata() != null) {
                        log.info(
                            "Post created event sent successfully: eventId={}, postId={}, partition={}, offset={}",
                            event.getEventId(),
                            event.getPostId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()
                        );
                    } else {
                        log.info("Post created event sent successfully: eventId={}, postId={}", event.getEventId(), event.getPostId());
                    }
                } else {
                    meterRegistry.counter("posts_created_total", "status", "failure").increment();
                    log.error("Failed to send post created event: eventId={}, postId={}, userId={}", event.getEventId(), event.getPostId(), event.getUserId(), ex);
                }
            });

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
            return;
        }

        publish.run();
    }

    private void publishNotificationEventAfterCommit(String messageKey, NotificationEvent event) {
        Runnable publish = () -> notificationKafkaTemplate.send(notificationsTopic, messageKey, event)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    meterRegistry.counter("notifications_sent_total", "status", "success").increment();
                    if (result != null && result.getRecordMetadata() != null) {
                        log.info(
                            "Notification event sent successfully: userId={}, type={}, partition={}, offset={}",
                            event.getUserId(),
                            event.getType(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()
                        );
                    } else {
                        log.info("Notification event sent successfully: userId={}, type={}", event.getUserId(), event.getType());
                    }
                } else {
                    meterRegistry.counter("notifications_sent_total", "status", "failure").increment();
                    log.error("Failed to send notification event: userId={}, type={}", event.getUserId(), event.getType(), ex);
                }
            });

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
            return;
        }

        publish.run();
    }

    public PostDto getPostById(Long postId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));
        return toDto(post);
    }

    public List<PostDto> getUserPosts(Long userId, Long currentUserId) {
        List<Post> posts = postRepository.findByUserIdAndParentPostIdIsNullOrderByCreatedAtDesc(userId);
        if (posts.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();

        var likeCounts = postLikeRepository.countByPostIds(postIds)
            .stream()
            .collect(Collectors.toMap(PostLikeRepository.PostLikeCount::getPostId, PostLikeRepository.PostLikeCount::getLikeCount));

        var dislikeCounts = postDislikeRepository.countByPostIds(postIds)
            .stream()
            .collect(Collectors.toMap(PostDislikeRepository.PostDislikeCount::getPostId, PostDislikeRepository.PostDislikeCount::getDislikeCount));

        Set<Long> likedPostIds = Set.copyOf(postLikeRepository.findLikedPostIdsByUserIdAndPostIds(currentUserId, postIds));
        Set<Long> dislikedPostIds = Set.copyOf(postDislikeRepository.findDislikedPostIdsByUserIdAndPostIds(currentUserId, postIds));

        return posts.stream()
            .map(post -> new PostDto(
                post.getId(),
                post.getUser().getId(),
                post.getUser().getUsername(),
                post.getUser().getDisplayName(),
                post.getContent(),
                post.getCreatedAt(),
                post.getParentPostId(),
                post.getSources(),
                post.getTopicTags(),
                post.getPostTrustScore() == null ? 0.0d : post.getPostTrustScore(),
                post.getAiVerdict() == null ? AiVerdict.PROCESSING.name() : post.getAiVerdict().name(),
                likeCounts.getOrDefault(post.getId(), 0L),
                likedPostIds.contains(post.getId()),
                dislikeCounts.getOrDefault(post.getId(), 0L),
                dislikedPostIds.contains(post.getId())
            ))
            .collect(Collectors.toList());
    }

    public Page<PostDto> getUserPostsPage(Long userId, Long currentUserId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<Post> postPage = postRepository.findByUserIdAndParentPostIdIsNullOrderByCreatedAtDesc(userId, pageable);
        if (postPage.isEmpty()) {
            return postPage.map(this::toDto);
        }

        List<Long> postIds = postPage.getContent().stream().map(Post::getId).toList();

        var likeCounts = postLikeRepository.countByPostIds(postIds)
            .stream()
            .collect(Collectors.toMap(PostLikeRepository.PostLikeCount::getPostId, PostLikeRepository.PostLikeCount::getLikeCount));

        var dislikeCounts = postDislikeRepository.countByPostIds(postIds)
            .stream()
            .collect(Collectors.toMap(PostDislikeRepository.PostDislikeCount::getPostId, PostDislikeRepository.PostDislikeCount::getDislikeCount));

        Set<Long> likedPostIds = Set.copyOf(postLikeRepository.findLikedPostIdsByUserIdAndPostIds(currentUserId, postIds));
        Set<Long> dislikedPostIds = Set.copyOf(postDislikeRepository.findDislikedPostIdsByUserIdAndPostIds(currentUserId, postIds));

        return postPage.map(post -> new PostDto(
            post.getId(),
            post.getUser().getId(),
            post.getUser().getUsername(),
            post.getUser().getDisplayName(),
            post.getContent(),
            post.getCreatedAt(),
            post.getParentPostId(),
            post.getSources(),
            post.getTopicTags(),
            post.getPostTrustScore() == null ? 0.0d : post.getPostTrustScore(),
            post.getAiVerdict() == null ? AiVerdict.PROCESSING.name() : post.getAiVerdict().name(),
            likeCounts.getOrDefault(post.getId(), 0L),
            likedPostIds.contains(post.getId()),
            dislikeCounts.getOrDefault(post.getId(), 0L),
            dislikedPostIds.contains(post.getId())
        ));
    }

    @Transactional
    public LikeStatusDto likePost(Long postId, Long userId) {
        Post post = postRepository.findByIdForUpdate(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        double globalTrust = user.getUserTrustScore() == null ? 1.0d : Math.max(0.0d, user.getUserTrustScore());
        double likerTrust = topicReputationService.resolveTrustForTopics(userId, post.getTopicTags(), globalTrust);
        double trustContribution = post.getUser().getId().equals(userId) ? 0.0d : likerTrust;

        int inserted = postLikeRepository.insertIgnore(postId, userId, trustContribution);
        if (inserted == 0) {
            meterRegistry.counter("post_likes_total", "operation", "like", "status", "duplicate").increment();
            return buildReactionStatus(postId, userId);
        }

        PostDislike removedDislike = postDislikeRepository.findByPostIdAndUserId(postId, userId).orElse(null);
        if (removedDislike != null) {
            postDislikeRepository.deleteByPostIdAndUserId(postId, userId);
            double removedDislikeContribution = removedDislike.getTrustContribution() == null ? 0.0d : removedDislike.getTrustContribution();
            if (!post.getUser().getId().equals(userId)) {
                double currentPostTrust = post.getPostTrustScore() == null ? 0.0d : post.getPostTrustScore();
                post.setPostTrustScore(currentPostTrust + removedDislikeContribution);
                trustAggregateService.recordPostTrustDelta(post.getUser().getId(), removedDislikeContribution);
                topicReputationService.recordReactionFeedbackForTopics(post.getUser().getId(), post.getTopicTags(), removedDislikeContribution, -1L);
            }
        }

        // Do not allow self-likes to increase trust scores.
        if (!post.getUser().getId().equals(userId)) {
            double currentPostTrust = post.getPostTrustScore() == null ? 0.0d : post.getPostTrustScore();
            post.setPostTrustScore(currentPostTrust + likerTrust);
            trustAggregateService.recordPostTrustDelta(post.getUser().getId(), likerTrust);
            topicReputationService.recordReactionFeedbackForTopics(post.getUser().getId(), post.getTopicTags(), likerTrust, 1L);
        }

        postRepository.save(post);
        meterRegistry.counter("post_likes_total", "operation", "like", "status", "success").increment();

        return buildReactionStatus(postId, true, false);
    }

    @Transactional
    public LikeStatusDto unlikePost(Long postId, Long userId) {
        Post post = postRepository.findByIdForUpdate(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));

        PostLike like = postLikeRepository.findByPostIdAndUserId(postId, userId).orElse(null);
        if (like == null) {
            meterRegistry.counter("post_likes_total", "operation", "unlike", "status", "duplicate").increment();
            return buildReactionStatus(postId, userId);
        }

        postLikeRepository.deleteByPostIdAndUserId(postId, userId);

        double trustContribution = like.getTrustContribution() == null ? 0.0d : like.getTrustContribution();

        if (!post.getUser().getId().equals(userId)) {
            double currentPostTrust = post.getPostTrustScore() == null ? 0.0d : post.getPostTrustScore();
            post.setPostTrustScore(Math.max(0.0d, currentPostTrust - trustContribution));
            postRepository.save(post);
            trustAggregateService.recordPostTrustDelta(post.getUser().getId(), -trustContribution);
            topicReputationService.recordReactionFeedbackForTopics(post.getUser().getId(), post.getTopicTags(), -trustContribution, -1L);
        }

        meterRegistry.counter("post_likes_total", "operation", "unlike", "status", "success").increment();
        return buildReactionStatus(postId, false, false);
    }

    @Transactional
    public LikeStatusDto dislikePost(Long postId, Long userId) {
        Post post = postRepository.findByIdForUpdate(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        double globalTrust = user.getUserTrustScore() == null ? 1.0d : Math.max(0.0d, user.getUserTrustScore());
        double dislikerTrust = topicReputationService.resolveTrustForTopics(userId, post.getTopicTags(), globalTrust);
        double trustContribution = post.getUser().getId().equals(userId) ? 0.0d : dislikerTrust;

        int inserted = postDislikeRepository.insertIgnore(postId, userId, trustContribution);
        if (inserted == 0) {
            meterRegistry.counter("post_dislikes_total", "operation", "dislike", "status", "duplicate").increment();
            return buildReactionStatus(postId, userId);
        }

        PostLike removedLike = postLikeRepository.findByPostIdAndUserId(postId, userId).orElse(null);
        if (removedLike != null) {
            postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            double removedLikeContribution = removedLike.getTrustContribution() == null ? 0.0d : removedLike.getTrustContribution();
            if (!post.getUser().getId().equals(userId)) {
                double currentPostTrust = post.getPostTrustScore() == null ? 0.0d : post.getPostTrustScore();
                post.setPostTrustScore(Math.max(0.0d, currentPostTrust - removedLikeContribution));
                trustAggregateService.recordPostTrustDelta(post.getUser().getId(), -removedLikeContribution);
                topicReputationService.recordReactionFeedbackForTopics(post.getUser().getId(), post.getTopicTags(), -removedLikeContribution, -1L);
            }
        }

        if (!post.getUser().getId().equals(userId)) {
            double currentPostTrust = post.getPostTrustScore() == null ? 0.0d : post.getPostTrustScore();
            post.setPostTrustScore(Math.max(0.0d, currentPostTrust - dislikerTrust));
            trustAggregateService.recordPostTrustDelta(post.getUser().getId(), -dislikerTrust);
            topicReputationService.recordReactionFeedbackForTopics(post.getUser().getId(), post.getTopicTags(), -dislikerTrust, 1L);
        }

        postRepository.save(post);
        meterRegistry.counter("post_dislikes_total", "operation", "dislike", "status", "success").increment();

        return buildReactionStatus(postId, false, true);
    }

    @Transactional
    public LikeStatusDto undislikePost(Long postId, Long userId) {
        Post post = postRepository.findByIdForUpdate(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));

        PostDislike dislike = postDislikeRepository.findByPostIdAndUserId(postId, userId).orElse(null);
        if (dislike == null) {
            meterRegistry.counter("post_dislikes_total", "operation", "undislike", "status", "duplicate").increment();
            return buildReactionStatus(postId, userId);
        }

        postDislikeRepository.deleteByPostIdAndUserId(postId, userId);

        double trustContribution = dislike.getTrustContribution() == null ? 0.0d : dislike.getTrustContribution();

        if (!post.getUser().getId().equals(userId)) {
            double currentPostTrust = post.getPostTrustScore() == null ? 0.0d : post.getPostTrustScore();
            post.setPostTrustScore(currentPostTrust + trustContribution);
            postRepository.save(post);
            trustAggregateService.recordPostTrustDelta(post.getUser().getId(), trustContribution);
            topicReputationService.recordReactionFeedbackForTopics(post.getUser().getId(), post.getTopicTags(), trustContribution, -1L);
        }

        meterRegistry.counter("post_dislikes_total", "operation", "undislike", "status", "success").increment();
        return buildReactionStatus(postId, false, false);
    }

    @Transactional(readOnly = true)
    public List<PostDto> getCounterpoints(Long parentPostId, Long currentUserId) {
        // Return all counterpoints for the parent post (previously limited to top 3)
        List<Post> counterpoints = postRepository.findByParentPostIdOrderByPostTrustScoreDescCreatedAtDesc(parentPostId);
        if (counterpoints.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = counterpoints.stream().map(Post::getId).toList();

        var likeCounts = postLikeRepository.countByPostIds(postIds)
            .stream()
            .collect(Collectors.toMap(PostLikeRepository.PostLikeCount::getPostId, PostLikeRepository.PostLikeCount::getLikeCount));

        var dislikeCounts = postDislikeRepository.countByPostIds(postIds)
            .stream()
            .collect(Collectors.toMap(PostDislikeRepository.PostDislikeCount::getPostId, PostDislikeRepository.PostDislikeCount::getDislikeCount));

        Set<Long> likedPostIds = Set.copyOf(postLikeRepository.findLikedPostIdsByUserIdAndPostIds(currentUserId, postIds));
        Set<Long> dislikedPostIds = Set.copyOf(postDislikeRepository.findDislikedPostIdsByUserIdAndPostIds(currentUserId, postIds));

        return counterpoints.stream()
            .map(post -> new PostDto(
                post.getId(),
                post.getUser().getId(),
                post.getUser().getUsername(),
                post.getUser().getDisplayName(),
                post.getContent(),
                post.getCreatedAt(),
                post.getParentPostId(),
                post.getSources(),
                post.getTopicTags(),
                post.getPostTrustScore() == null ? 0.0d : post.getPostTrustScore(),
                post.getAiVerdict() == null ? AiVerdict.PROCESSING.name() : post.getAiVerdict().name(),
                likeCounts.getOrDefault(post.getId(), 0L),
                likedPostIds.contains(post.getId()),
                dislikeCounts.getOrDefault(post.getId(), 0L),
                dislikedPostIds.contains(post.getId())
            ))
            .collect(Collectors.toList());
    }

    private PostDto toDto(Post post) {
        return new PostDto(
            post.getId(),
            post.getUser().getId(),
            post.getUser().getUsername(),
            post.getUser().getDisplayName(),
            post.getContent(),
            post.getCreatedAt(),
            post.getParentPostId(),
            post.getSources(),
            post.getTopicTags(),
            post.getPostTrustScore() == null ? 0.0d : post.getPostTrustScore(),
            post.getAiVerdict() == null ? AiVerdict.PROCESSING.name() : post.getAiVerdict().name(),
            postLikeRepository.countByPostId(post.getId()),
            false,
            postDislikeRepository.countByPostId(post.getId()),
            false
        );
    }

    private LikeStatusDto buildReactionStatus(Long postId, Long userId) {
        return new LikeStatusDto(
            postId,
            postLikeRepository.countByPostId(postId),
            postLikeRepository.existsByPostIdAndUserId(postId, userId),
            postDislikeRepository.countByPostId(postId),
            postDislikeRepository.existsByPostIdAndUserId(postId, userId)
        );
    }

    private LikeStatusDto buildReactionStatus(Long postId, boolean likedByMe, boolean dislikedByMe) {
        return new LikeStatusDto(
            postId,
            postLikeRepository.countByPostId(postId),
            likedByMe,
            postDislikeRepository.countByPostId(postId),
            dislikedByMe
        );
    }

    private List<String> sanitizeAndValidateSources(List<String> requestedSources) {
        if (requestedSources == null || requestedSources.isEmpty()) {
            throw new RuntimeException("At least one source is required");
        }

        List<String> sources = requestedSources.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(source -> !source.isEmpty())
            .toList();

        if (sources.isEmpty()) {
            throw new RuntimeException("At least one source is required");
        }

        for (String source : sources) {
            validateSourceUrl(source);
        }

        return sources;
    }

    private List<String> sanitizeAndValidateTopicTags(List<String> requestedTopicTags) {
        if (requestedTopicTags == null || requestedTopicTags.isEmpty()) {
            return List.of();
        }

        List<String> topicTags = requestedTopicTags.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .map(tag -> tag.toLowerCase(Locale.ROOT))
            .filter(tag -> !tag.isEmpty())
            .distinct()
            .toList();

        if (topicTags.size() > 3) {
            throw new RuntimeException("A post can have at most 3 topic tags");
        }

        List<String> invalidTags = topicTags.stream()
            .filter(tag -> !ALLOWED_TOPIC_TAGS.contains(tag))
            .toList();

        if (!invalidTags.isEmpty()) {
            throw new RuntimeException("Unsupported topic tag(s): " + String.join(", ", invalidTags));
        }

        return topicTags;
    }

    private void validateSourceUrl(String source) {
        URI uri;
        try {
            uri = new URI(source);
        } catch (URISyntaxException ex) {
            throw new RuntimeException("Invalid source URL: " + source);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new RuntimeException("Source URL must use http or https: " + source);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new RuntimeException("Source URL must include a valid host: " + source);
        }
    }

    private double computeSourceTrustBoost(List<String> sources) {
        Set<String> trustedDomains = Arrays.stream(trustedSourceDomains.split(","))
            .map(String::trim)
            .filter(domain -> !domain.isEmpty())
            .map(domain -> domain.toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(HashSet::new));

        if (trustedDomains.isEmpty()) {
            return 0.0d;
        }

        Set<String> matchedTrustedDomains = new LinkedHashSet<>();
        for (String source : sources) {
            String host = extractHost(source);
            if (host == null) {
                continue;
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);
            for (String trustedDomain : trustedDomains) {
                if (normalizedHost.equals(trustedDomain) || normalizedHost.endsWith("." + trustedDomain)) {
                    matchedTrustedDomains.add(trustedDomain);
                }
            }
        }

        double boost = matchedTrustedDomains.size() * Math.max(0.0d, trustedSourceBoostPerMatch);
        return Math.min(Math.max(0.0d, maxSourceTrustBoost), boost);
    }

    private double computeCreatorDomainTrustBoost(double creatorDomainTrust) {
        double weighted = Math.max(0.0d, creatorDomainTrust) * Math.max(0.0d, creatorDomainTrustWeight);
        return Math.min(Math.max(0.0d, maxCreatorDomainBoost), weighted);
    }

    public List<String> getSupportedTopicTags() {
        return ALLOWED_TOPIC_TAGS.stream().sorted().toList();
    }

    private String extractHost(String source) {
        try {
            URI uri = new URI(source);
            return uri.getHost();
        } catch (URISyntaxException ex) {
            return null;
        }
    }
}