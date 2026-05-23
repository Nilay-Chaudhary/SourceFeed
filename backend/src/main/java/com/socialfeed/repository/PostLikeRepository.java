package com.socialfeed.repository;

import com.socialfeed.model.PostLike;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    interface PostLikeCount {
        Long getPostId();
        Long getLikeCount();
    }

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    long countByPostId(Long postId);

    @Modifying
    @Query(value = """
        INSERT INTO post_likes (post_id, user_id, trust_contribution, created_at)
        VALUES (:postId, :userId, :trustContribution, NOW())
        ON CONFLICT (post_id, user_id) DO NOTHING
        """, nativeQuery = true)
    int insertIgnore(@Param("postId") Long postId, @Param("userId") Long userId, @Param("trustContribution") Double trustContribution);

    long deleteByPostIdAndUserId(Long postId, Long userId);

    @Query("""
        SELECT pl.post.id AS postId, COUNT(pl.id) AS likeCount
        FROM PostLike pl
        WHERE pl.post.id IN :postIds
        GROUP BY pl.post.id
        """)
    List<PostLikeCount> countByPostIds(@Param("postIds") List<Long> postIds);

    @Query("""
        SELECT pl.post.id
        FROM PostLike pl
        WHERE pl.user.id = :userId AND pl.post.id IN :postIds
        """)
    List<Long> findLikedPostIdsByUserIdAndPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);

    @Query("""
        SELECT pl.post.id
        FROM PostLike pl
        WHERE pl.user.id = :userId
        ORDER BY pl.createdAt DESC
        """)
    List<Long> findLikedPostIdsByUserId(@Param("userId") Long userId, Pageable pageable);
}