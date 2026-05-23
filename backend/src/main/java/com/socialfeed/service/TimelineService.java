package com.socialfeed.service;

import com.socialfeed.dto.TimelineCursorPageDto;
import com.socialfeed.dto.PostDto;
import com.socialfeed.model.Post;
import com.socialfeed.model.TimelineEntry;
import com.socialfeed.repository.PostDislikeRepository;
import com.socialfeed.repository.PostLikeRepository;
import com.socialfeed.repository.PostRepository;
import com.socialfeed.repository.TimelineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimelineService {

    private static final int MAX_TIMELINE_PAGE_SIZE = 100;

    private final TimelineRepository timelineRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostDislikeRepository postDislikeRepository;

    public List<PostDto> getUserTimeline(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TimelineEntry> entries = timelineRepository.findByUserIdOrderByPostCreatedAtDesc(userId, pageable);
        log.debug("Timeline fetched: userId={}, page={}, size={}, returnedEntries={}", userId, page, size, entries.getNumberOfElements());

        return mapTimelineEntriesToPostDtos(userId, entries.getContent());
    }

    public TimelineCursorPageDto getUserTimelineByCursor(Long userId, String cursor, int size) {
        int normalizedSize = Math.max(1, Math.min(size, MAX_TIMELINE_PAGE_SIZE));
        Pageable pageable = PageRequest.of(0, normalizedSize + 1);

        List<TimelineEntry> loadedEntries;
        if (cursor == null || cursor.isBlank()) {
            loadedEntries = timelineRepository.findFirstPageByUserIdOrderByPostCreatedAtDescPostIdDesc(userId, pageable);
        } else {
            CursorToken token = parseCursor(cursor);
            loadedEntries = timelineRepository.findByUserIdAfterCursorOrderByPostCreatedAtDescPostIdDesc(
                userId,
                token.postCreatedAt(),
                token.postId(),
                pageable
            );
        }

        boolean hasMore = loadedEntries.size() > normalizedSize;
        List<TimelineEntry> pageEntries = hasMore
            ? loadedEntries.subList(0, normalizedSize)
            : loadedEntries;

        List<PostDto> items = mapTimelineEntriesToPostDtos(userId, pageEntries);
        String nextCursor = null;
        if (hasMore && !pageEntries.isEmpty()) {
            TimelineEntry lastEntry = pageEntries.get(pageEntries.size() - 1);
            nextCursor = encodeCursor(lastEntry.getPostCreatedAt(), lastEntry.getPostId());
        }

        return new TimelineCursorPageDto(items, nextCursor, hasMore);
    }

    private List<PostDto> mapTimelineEntriesToPostDtos(Long userId, List<TimelineEntry> timelineEntries) {
        if (timelineEntries.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = timelineEntries.stream()
            .map(TimelineEntry::getPostId)
            .distinct()
            .toList();

        Map<Long, Post> postsById = postRepository.findByIdInWithUser(postIds).stream()
            .collect(Collectors.toMap(Post::getId, post -> post));

        List<TimelineEntry> rootTimelineEntries = timelineEntries.stream()
            .filter(entry -> {
                Post post = postsById.get(entry.getPostId());
                return post != null && post.getParentPostId() == null;
            })
            .toList();

        if (rootTimelineEntries.isEmpty()) {
            return List.of();
        }

        List<Long> visiblePostIds = rootTimelineEntries.stream()
            .map(TimelineEntry::getPostId)
            .distinct()
            .toList();

        Map<Long, Long> likeCounts = postLikeRepository.countByPostIds(visiblePostIds)
            .stream()
            .collect(Collectors.toMap(PostLikeRepository.PostLikeCount::getPostId, PostLikeRepository.PostLikeCount::getLikeCount));

        Map<Long, Long> dislikeCounts = postDislikeRepository.countByPostIds(visiblePostIds)
            .stream()
            .collect(Collectors.toMap(PostDislikeRepository.PostDislikeCount::getPostId, PostDislikeRepository.PostDislikeCount::getDislikeCount));

        Set<Long> likedPostIds = Set.copyOf(postLikeRepository.findLikedPostIdsByUserIdAndPostIds(userId, visiblePostIds));
        Set<Long> dislikedPostIds = Set.copyOf(postDislikeRepository.findDislikedPostIdsByUserIdAndPostIds(userId, visiblePostIds));

        Map<Long, String> usernamesById = postsById.values().stream()
            .collect(Collectors.toMap(post -> post.getUser().getId(), post -> post.getUser().getUsername(), (existing, replacement) -> existing));

        Map<Long, String> displayNamesById = postsById.values().stream()
            .collect(Collectors.toMap(post -> post.getUser().getId(), post -> post.getUser().getDisplayName(), (existing, replacement) -> existing));

        return rootTimelineEntries.stream()
            .map(entry -> toPostDto(
                entry,
                postsById.get(entry.getPostId()),
                usernamesById.get(entry.getPostUserId()),
                displayNamesById.get(entry.getPostUserId()),
                likeCounts.getOrDefault(entry.getPostId(), 0L),
                likedPostIds.contains(entry.getPostId()),
                dislikeCounts.getOrDefault(entry.getPostId(), 0L),
                dislikedPostIds.contains(entry.getPostId())
            ))
            .collect(Collectors.toList());
    }

    private CursorToken parseCursor(String cursor) {
        String[] parts = cursor.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid cursor format");
        }

        try {
            long epochMillis = Long.parseLong(parts[0]);
            long postId = Long.parseLong(parts[1]);
            LocalDateTime postCreatedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
            return new CursorToken(postCreatedAt, postId);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid cursor format", ex);
        }
    }

    private String encodeCursor(LocalDateTime postCreatedAt, Long postId) {
        long epochMillis = postCreatedAt.toInstant(ZoneOffset.UTC).toEpochMilli();
        return epochMillis + ":" + postId;
    }

    private record CursorToken(LocalDateTime postCreatedAt, Long postId) {
    }

    @Transactional
    public void addToTimeline(Long userId, Long postId, Long postUserId, String postContent, 
                              java.time.LocalDateTime postCreatedAt) {
        // Check if already exists (idempotency)
        if (timelineRepository.existsByUserIdAndPostId(userId, postId)) {
            log.debug("Timeline entry already exists, skipping: userId={}, postId={}", userId, postId);
            return;
        }

        TimelineEntry entry = new TimelineEntry();
        entry.setUserId(userId);
        entry.setPostId(postId);
        entry.setPostUserId(postUserId);
        entry.setPostContent(postContent);
        entry.setPostCreatedAt(postCreatedAt);

        try {
            timelineRepository.save(entry);
            log.debug("Timeline entry inserted: userId={}, postId={}, authorId={}, postCreatedAt={}", userId, postId, postUserId, postCreatedAt);
        } catch (DataIntegrityViolationException ex) {
            log.info("Timeline entry already exists due to concurrent write, skipping: userId={}, postId={}", userId, postId);
        }
    }

    @Transactional
    public void addToTimelineBatch(List<Long> userIds, Long postId, Long postUserId, String postContent,
                                   java.time.LocalDateTime postCreatedAt) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        // Find existing entries to avoid duplicates
        List<TimelineEntry> existing = timelineRepository.findByPostIdAndUserIdIn(postId, userIds);
        Set<Long> existingUserIds = existing.stream().map(TimelineEntry::getUserId).collect(Collectors.toSet());

        List<TimelineEntry> entriesToInsert = userIds.stream()
            .filter(uid -> !existingUserIds.contains(uid))
            .map(uid -> {
                TimelineEntry e = new TimelineEntry();
                e.setUserId(uid);
                e.setPostId(postId);
                e.setPostUserId(postUserId);
                e.setPostContent(postContent);
                e.setPostCreatedAt(postCreatedAt);
                return e;
            })
            .toList();

        if (entriesToInsert.isEmpty()) {
            return;
        }

        try {
            timelineRepository.saveAll(entriesToInsert);
            log.debug("Batch inserted {} timeline entries for postId={}", entriesToInsert.size(), postId);
        } catch (DataIntegrityViolationException ex) {
            log.info("Some timeline entries already existed during batch insert, continuing: postId={}", postId);
        }
    }

    @Transactional
    public int backfillRecentPostsForFollow(Long followerId, Long followeeId, int limit) {
        List<Post> recentPosts = postRepository.findTop20ByUserIdAndParentPostIdIsNullOrderByCreatedAtDesc(followeeId);
        if (recentPosts.isEmpty()) {
            log.info("Follow backfill skipped - no posts found: followerId={}, followeeId={}", followerId, followeeId);
            return 0;
        }

        List<Post> candidatePosts = recentPosts.subList(0, Math.min(limit, recentPosts.size()));
        List<Long> postIds = candidatePosts.stream().map(Post::getId).toList();
        Set<Long> existingPostIds = timelineRepository.findByUserIdAndPostIdIn(followerId, postIds)
            .stream()
            .map(TimelineEntry::getPostId)
            .collect(Collectors.toCollection(HashSet::new));

        List<TimelineEntry> entriesToInsert = candidatePosts.stream()
            .filter(post -> !existingPostIds.contains(post.getId()))
            .map(post -> {
                TimelineEntry entry = new TimelineEntry();
                entry.setUserId(followerId);
                entry.setPostId(post.getId());
                entry.setPostUserId(followeeId);
                entry.setPostContent(post.getContent());
                entry.setPostCreatedAt(post.getCreatedAt());
                return entry;
            })
            .toList();

        if (!entriesToInsert.isEmpty()) {
            try {
                timelineRepository.saveAll(entriesToInsert);
            } catch (DataIntegrityViolationException ex) {
                log.info("Some timeline entries already existed during follow backfill, continuing: followerId={}, followeeId={}", followerId, followeeId);
            }
        }

        int added = entriesToInsert.size();

        log.info(
            "Follow backfill completed: followerId={}, followeeId={}, requestedLimit={}, candidatePosts={}, addedEntries={}",
            followerId, followeeId, limit, candidatePosts.size(), added
        );
        return added;
    }

    private PostDto toPostDto(TimelineEntry entry, Post post, String username, String displayName, long likeCount, boolean likedByMe, long dislikeCount, boolean dislikedByMe) {
        return new PostDto(
            entry.getPostId(),
            entry.getPostUserId(),
            username,
            displayName,
            entry.getPostContent(),
            entry.getPostCreatedAt(),
            post == null ? null : post.getParentPostId(),
            post == null ? List.of() : post.getSources(),
            post == null ? List.of() : post.getTopicTags(),
            post == null || post.getPostTrustScore() == null ? 0.0d : post.getPostTrustScore(),
            post == null || post.getAiVerdict() == null ? com.socialfeed.model.AiVerdict.PROCESSING.name() : post.getAiVerdict().name(),
            likeCount,
            likedByMe,
            dislikeCount,
            dislikedByMe
        );
    }
}
