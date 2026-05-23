package com.socialfeed.repository;

import com.socialfeed.model.TimelineEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TimelineRepository extends JpaRepository<TimelineEntry, Long> {
    Page<TimelineEntry> findByUserIdOrderByPostCreatedAtDesc(Long userId, Pageable pageable);

    @Query("""
        SELECT t
        FROM TimelineEntry t
        WHERE t.userId = :userId
        ORDER BY t.postCreatedAt DESC, t.postId DESC
        """)
    List<TimelineEntry> findFirstPageByUserIdOrderByPostCreatedAtDescPostIdDesc(@Param("userId") Long userId, Pageable pageable);

    @Query("""
        SELECT t
        FROM TimelineEntry t
        WHERE t.userId = :userId
          AND (
            t.postCreatedAt < :cursorTime
            OR (t.postCreatedAt = :cursorTime AND t.postId < :cursorPostId)
          )
        ORDER BY t.postCreatedAt DESC, t.postId DESC
        """)
    List<TimelineEntry> findByUserIdAfterCursorOrderByPostCreatedAtDescPostIdDesc(
        @Param("userId") Long userId,
        @Param("cursorTime") java.time.LocalDateTime cursorTime,
        @Param("cursorPostId") Long cursorPostId,
        Pageable pageable
    );

    boolean existsByUserIdAndPostId(Long userId, Long postId);
    List<TimelineEntry> findByUserIdAndPostIdIn(Long userId, List<Long> postIds);
    List<TimelineEntry> findByPostIdAndUserIdIn(Long postId, List<Long> userIds);
}
