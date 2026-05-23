package com.socialfeed.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialfeed.dto.PostDto;
import com.socialfeed.dto.PostSearchResultDto;
import com.socialfeed.event.PostCreatedEvent;
import com.socialfeed.model.Post;
import com.socialfeed.model.PostEmbedding;
import com.socialfeed.model.User;
import com.socialfeed.model.UserProfileEmbedding;
import com.socialfeed.repository.FollowRepository;
import com.socialfeed.repository.PostDislikeRepository;
import com.socialfeed.repository.PostEmbeddingRepository;
import com.socialfeed.repository.PostLikeRepository;
import com.socialfeed.repository.PostRepository;
import com.socialfeed.repository.UserProfileEmbeddingRepository;
import com.socialfeed.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private static final String MODEL_VERSION = "simple-hash-v1";
    private static final String PROFILE_MODEL_VERSION = "profile-simple-hash-v1";
    private static final int EMBEDDING_DIMENSIONS = 32;
    private static final int LIKED_SAMPLE_SIZE = 100;
    private static final int AUTHORED_SAMPLE_SIZE = 50;
    private static final int FOLLOWED_RECENT_SAMPLE_SIZE = 100;
    private static final int FOR_YOU_CANDIDATE_WINDOW = 200;
    private static final double LIKED_WEIGHT = 0.6d;
    private static final double AUTHORED_WEIGHT = 0.3d;
    private static final double FOLLOWED_RECENT_WEIGHT = 0.1d;

    private final PostEmbeddingRepository postEmbeddingRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostDislikeRepository postDislikeRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final UserProfileEmbeddingRepository userProfileEmbeddingRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void enrichPost(PostCreatedEvent event) {
        Post post = postRepository.findById(event.getPostId())
            .orElseThrow(() -> new RuntimeException("Post not found for embedding generation"));

        double[] embedding = generateEmbedding(event.getContent());
        PostEmbedding postEmbedding = postEmbeddingRepository.findByPostId(post.getId())
            .orElseGet(PostEmbedding::new);

        postEmbedding.setPost(post);
        postEmbedding.setEmbeddingJson(serializeEmbedding(embedding));
        postEmbedding.setModelVersion(MODEL_VERSION);
        postEmbedding.setGeneratedAt(LocalDateTime.now());

        postEmbeddingRepository.save(postEmbedding);
        log.info("Embedding generated and stored: postId={}, eventId={}, modelVersion={}", post.getId(), event.getEventId(), MODEL_VERSION);
    }

    @Transactional(readOnly = true)
    public List<PostSearchResultDto> searchPosts(String query, int limit, Long currentUserId) {
        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.isEmpty() || limit <= 0) {
            return List.of();
        }

        double[] queryEmbedding = generateEmbedding(trimmedQuery);
        List<PostEmbedding> embeddings = postEmbeddingRepository.findAll();
        if (embeddings.isEmpty()) {
            return List.of();
        }

        List<ScoredPost> ranked = embeddings.stream()
            .filter(postEmbedding -> postEmbedding.getPost() != null && postEmbedding.getPost().getParentPostId() == null)
            .map(postEmbedding -> {
                double[] storedEmbedding = deserializeEmbedding(postEmbedding.getEmbeddingJson());
                double score = cosineSimilarity(queryEmbedding, storedEmbedding);
                return new ScoredPost(postEmbedding.getPost(), score);
            })
            .filter(scoredPost -> scoredPost.score() > 0.0d)
            .sorted(Comparator.comparingDouble(ScoredPost::score).reversed())
            .limit(limit)
            .toList();

        if (ranked.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = ranked.stream().map(scoredPost -> scoredPost.post().getId()).toList();
        Map<Long, Long> likeCounts = postLikeRepository.countByPostIds(postIds)
            .stream()
            .collect(Collectors.toMap(PostLikeRepository.PostLikeCount::getPostId, PostLikeRepository.PostLikeCount::getLikeCount));
        Map<Long, Long> dislikeCounts = postDislikeRepository.countByPostIds(postIds)
            .stream()
            .collect(Collectors.toMap(PostDislikeRepository.PostDislikeCount::getPostId, PostDislikeRepository.PostDislikeCount::getDislikeCount));

        Set<Long> likedPostIds = Set.copyOf(postLikeRepository.findLikedPostIdsByUserIdAndPostIds(currentUserId, postIds));
        Set<Long> dislikedPostIds = Set.copyOf(postDislikeRepository.findDislikedPostIdsByUserIdAndPostIds(currentUserId, postIds));
        List<Long> authorIds = ranked.stream().map(scoredPost -> scoredPost.post().getUser().getId()).distinct().toList();
        Map<Long, User> authors = userRepository.findByIdIn(authorIds).stream()
            .collect(Collectors.toMap(User::getId, user -> user));

        List<PostSearchResultDto> results = new ArrayList<>();
        for (ScoredPost scoredPost : ranked) {
            Post post = scoredPost.post();
            User author = authors.get(post.getUser().getId());
            if (author == null) {
                continue;
            }

            PostDto postDto = new PostDto(
                post.getId(),
                author.getId(),
                author.getUsername(),
                author.getDisplayName(),
                post.getContent(),
                post.getCreatedAt(),
                post.getParentPostId(),
                post.getSources(),
                post.getTopicTags(),
                post.getPostTrustScore() == null ? 0.0d : post.getPostTrustScore(),
                post.getAiVerdict() == null ? com.socialfeed.model.AiVerdict.PROCESSING.name() : post.getAiVerdict().name(),
                likeCounts.getOrDefault(post.getId(), 0L),
                likedPostIds.contains(post.getId()),
                dislikeCounts.getOrDefault(post.getId(), 0L),
                dislikedPostIds.contains(post.getId())
            );
            results.add(new PostSearchResultDto(postDto, scoredPost.score()));
        }

        return results;
    }

    @Transactional(readOnly = true)
    public List<PostDto> getForYouPosts(Long userId, int page, int size) {
        if (size <= 0) {
            return List.of();
        }

        UserProfileEmbedding profileEmbedding = userProfileEmbeddingRepository.findByUserId(userId).orElse(null);

        if (profileEmbedding == null) {
            return fallbackRecentPosts(userId, page, size);
        }

        double[] profileVector = deserializeEmbedding(profileEmbedding.getEmbeddingJson());
        List<Post> candidates = postRepository.findRecentForForYou(userId, PageRequest.of(0, FOR_YOU_CANDIDATE_WINDOW));
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Long> candidatePostIds = candidates.stream().map(Post::getId).toList();
        Map<Long, PostEmbedding> embeddingByPostId = postEmbeddingRepository.findByPostIdIn(candidatePostIds).stream()
            .collect(Collectors.toMap(pe -> pe.getPost().getId(), pe -> pe));

        List<ScoredPost> ranked = candidates.stream()
            .map(post -> {
                PostEmbedding postEmbedding = embeddingByPostId.get(post.getId());
                if (postEmbedding == null) {
                    return null;
                }
                double[] postVector = deserializeEmbedding(postEmbedding.getEmbeddingJson());
                double similarity = cosineSimilarity(profileVector, postVector);
                return new ScoredPost(post, similarity);
            })
            .filter(scoredPost -> scoredPost != null)
            .sorted(Comparator.comparingDouble(ScoredPost::score).reversed())
            .toList();

        if (ranked.isEmpty()) {
            return fallbackRecentPosts(userId, page, size);
        }

        int from = page * size;
        if (from >= ranked.size()) {
            return List.of();
        }
        int to = Math.min(from + size, ranked.size());
        List<Post> pagedPosts = ranked.subList(from, to).stream().map(ScoredPost::post).toList();
        return mapPostsToDto(pagedPosts, userId);
    }

    @Transactional
    public void recomputeUserProfileEmbedding(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found for profile embedding generation"));

        List<Long> likedPostIds = postLikeRepository.findLikedPostIdsByUserId(userId, PageRequest.of(0, LIKED_SAMPLE_SIZE));
        List<Long> authoredPostIds = postRepository.findTop50ByUserIdAndParentPostIdIsNullOrderByCreatedAtDesc(userId).stream()
            .limit(AUTHORED_SAMPLE_SIZE)
            .map(Post::getId)
            .toList();

        List<Long> followeeIds = followRepository.findFolloweeIdsByFollowerId(userId);
        List<Long> followedRecentPostIds = followeeIds.isEmpty()
            ? List.of()
            : postRepository.findRecentByUserIds(followeeIds, PageRequest.of(0, FOLLOWED_RECENT_SAMPLE_SIZE))
                .stream()
                .map(Post::getId)
                .toList();

        double[] combined = new double[EMBEDDING_DIMENSIONS];
        double activeWeight = 0.0d;

        double[] likedCentroid = centroidForPostIds(likedPostIds);
        if (likedCentroid != null) {
            addWeighted(combined, likedCentroid, LIKED_WEIGHT);
            activeWeight += LIKED_WEIGHT;
        }

        double[] authoredCentroid = centroidForPostIds(authoredPostIds);
        if (authoredCentroid != null) {
            addWeighted(combined, authoredCentroid, AUTHORED_WEIGHT);
            activeWeight += AUTHORED_WEIGHT;
        }

        double[] followedCentroid = centroidForPostIds(followedRecentPostIds);
        if (followedCentroid != null) {
            addWeighted(combined, followedCentroid, FOLLOWED_RECENT_WEIGHT);
            activeWeight += FOLLOWED_RECENT_WEIGHT;
        }

        if (activeWeight > 0.0d) {
            for (int i = 0; i < combined.length; i++) {
                combined[i] /= activeWeight;
            }
            normalizeInPlace(combined);
        }

        UserProfileEmbedding profileEmbedding = userProfileEmbeddingRepository.findByUserId(userId)
            .orElseGet(UserProfileEmbedding::new);
        profileEmbedding.setUser(user);
        profileEmbedding.setEmbeddingJson(serializeEmbedding(combined));
        profileEmbedding.setModelVersion(PROFILE_MODEL_VERSION);
        profileEmbedding.setUpdatedAt(LocalDateTime.now());

        userProfileEmbeddingRepository.save(profileEmbedding);
        log.info(
            "User profile embedding recomputed: userId={}, likedPosts={}, authoredPosts={}, followedRecentPosts={}, activeWeight={}",
            userId,
            likedPostIds.size(),
            authoredPostIds.size(),
            followedRecentPostIds.size(),
            activeWeight
        );
    }

    private double[] generateEmbedding(String text) {
        double[] vector = new double[EMBEDDING_DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vector;
        }

        String[] tokens = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            int index = Math.floorMod(token.hashCode(), EMBEDDING_DIMENSIONS);
            vector[index] += 1.0d;
        }

        double norm = 0.0d;
        for (double value : vector) {
            norm += value * value;
        }

        norm = Math.sqrt(norm);
        if (norm == 0.0d) {
            return vector;
        }

        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
        return vector;
    }

    private double cosineSimilarity(double[] left, double[] right) {
        int length = Math.min(left.length, right.length);
        double dotProduct = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;

        for (int i = 0; i < length; i++) {
            dotProduct += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }

        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            return 0.0d;
        }
        return dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private List<PostDto> fallbackRecentPosts(Long userId, int page, int size) {
        List<Post> fallback = postRepository.findRecentForForYou(userId, PageRequest.of(page, size));
        return mapPostsToDto(fallback, userId);
    }

    private List<PostDto> mapPostsToDto(List<Post> posts, Long currentUserId) {
        if (posts.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, Long> likeCounts = postLikeRepository.countByPostIds(postIds)
            .stream()
            .collect(Collectors.toMap(PostLikeRepository.PostLikeCount::getPostId, PostLikeRepository.PostLikeCount::getLikeCount));
        Map<Long, Long> dislikeCounts = postDislikeRepository.countByPostIds(postIds)
            .stream()
            .collect(Collectors.toMap(PostDislikeRepository.PostDislikeCount::getPostId, PostDislikeRepository.PostDislikeCount::getDislikeCount));
        Set<Long> likedPostIds = Set.copyOf(postLikeRepository.findLikedPostIdsByUserIdAndPostIds(currentUserId, postIds));
        Set<Long> dislikedPostIds = Set.copyOf(postDislikeRepository.findDislikedPostIdsByUserIdAndPostIds(currentUserId, postIds));

        return posts.stream()
            .map(post -> {
                User author = post.getUser();
                return new PostDto(
                    post.getId(),
                    author.getId(),
                    author.getUsername(),
                    author.getDisplayName(),
                    post.getContent(),
                    post.getCreatedAt(),
                    post.getParentPostId(),
                    post.getSources(),
                    post.getTopicTags(),
                    post.getPostTrustScore() == null ? 0.0d : post.getPostTrustScore(),
                    post.getAiVerdict() == null ? com.socialfeed.model.AiVerdict.PROCESSING.name() : post.getAiVerdict().name(),
                    likeCounts.getOrDefault(post.getId(), 0L),
                    likedPostIds.contains(post.getId()),
                    dislikeCounts.getOrDefault(post.getId(), 0L),
                    dislikedPostIds.contains(post.getId())
                );
            })
            .toList();
    }

    private double[] centroidForPostIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return null;
        }

        List<PostEmbedding> embeddings = postEmbeddingRepository.findByPostIdIn(postIds);
        if (embeddings.isEmpty()) {
            return null;
        }

        double[] centroid = new double[EMBEDDING_DIMENSIONS];
        int count = 0;
        for (PostEmbedding postEmbedding : embeddings) {
            double[] vector = deserializeEmbedding(postEmbedding.getEmbeddingJson());
            if (vector.length == 0) {
                continue;
            }
            int length = Math.min(centroid.length, vector.length);
            for (int i = 0; i < length; i++) {
                centroid[i] += vector[i];
            }
            count++;
        }

        if (count == 0) {
            return null;
        }

        for (int i = 0; i < centroid.length; i++) {
            centroid[i] /= count;
        }
        normalizeInPlace(centroid);
        return centroid;
    }

    private void addWeighted(double[] target, double[] source, double weight) {
        int length = Math.min(target.length, source.length);
        for (int i = 0; i < length; i++) {
            target[i] += source[i] * weight;
        }
    }

    private void normalizeInPlace(double[] vector) {
        double norm = 0.0d;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm == 0.0d) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }

    private String serializeEmbedding(double[] embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize embedding", ex);
        }
    }

    private double[] deserializeEmbedding(String embeddingJson) {
        try {
            return objectMapper.readValue(embeddingJson, double[].class);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to deserialize embedding", ex);
        }
    }

    private record ScoredPost(Post post, Double score) {
    }
}