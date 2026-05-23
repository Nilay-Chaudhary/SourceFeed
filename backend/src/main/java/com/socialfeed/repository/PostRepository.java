package com.socialfeed.repository;

import com.socialfeed.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    @EntityGraph(attributePaths = "sources")
    Optional<Post> findWithSourcesById(Long postId);

    @EntityGraph(attributePaths = "user")
    List<Post> findByUserIdAndParentPostIdIsNullOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "user")
    Page<Post> findByUserIdAndParentPostIdIsNullOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Post> findTop20ByUserIdAndParentPostIdIsNullOrderByCreatedAtDesc(Long userId);
    List<Post> findTop50ByUserIdAndParentPostIdIsNullOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndParentPostIdIsNull(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p WHERE p.id = :postId")
    Optional<Post> findByIdForUpdate(@Param("postId") Long postId);

    @Query("""
        SELECT p
        FROM Post p
        WHERE p.user.id IN :userIds
        ORDER BY p.createdAt DESC
        """)
    @EntityGraph(attributePaths = "user")
    List<Post> findRecentByUserIds(@Param("userIds") List<Long> userIds, Pageable pageable);

    @Query("""
        SELECT p
        FROM Post p
        WHERE p.user.id <> :userId AND p.parentPostId IS NULL
        ORDER BY p.createdAt DESC
        """)
    @EntityGraph(attributePaths = "user")
    List<Post> findRecentForForYou(@Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query("""
        SELECT p
        FROM Post p
        WHERE p.id IN :postIds
        """)
    List<Post> findByIdInWithUser(@Param("postIds") List<Long> postIds);

    @EntityGraph(attributePaths = "user")
    List<Post> findByParentPostIdOrderByPostTrustScoreDescCreatedAtDesc(Long parentPostId);

    @EntityGraph(attributePaths = "user")
    List<Post> findTop3ByParentPostIdOrderByPostTrustScoreDescCreatedAtDesc(Long parentPostId);
}
