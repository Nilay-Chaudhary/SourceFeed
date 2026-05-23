package com.socialfeed.repository;

import com.socialfeed.model.PostDislike;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostDislikeRepository extends JpaRepository<PostDislike, Long> {

    interface PostDislikeCount {
        Long getPostId();
        Long getDislikeCount();
    }

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    Optional<PostDislike> findByPostIdAndUserId(Long postId, Long userId);

    long countByPostId(Long postId);

    @Modifying
    @Query(value = """
        INSERT INTO post_dislikes (post_id, user_id, trust_contribution, created_at)
        VALUES (:postId, :userId, :trustContribution, NOW())
        ON CONFLICT (post_id, user_id) DO NOTHING
        """, nativeQuery = true)
    int insertIgnore(@Param("postId") Long postId, @Param("userId") Long userId, @Param("trustContribution") Double trustContribution);

    long deleteByPostIdAndUserId(Long postId, Long userId);

    @Query("""
        SELECT pd.post.id AS postId, COUNT(pd.id) AS dislikeCount
        FROM PostDislike pd
        WHERE pd.post.id IN :postIds
        GROUP BY pd.post.id
        """)
    List<PostDislikeCount> countByPostIds(@Param("postIds") List<Long> postIds);

    @Query("""
        SELECT pd.post.id
        FROM PostDislike pd
        WHERE pd.user.id = :userId AND pd.post.id IN :postIds
        """)
    List<Long> findDislikedPostIdsByUserIdAndPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);

    @Query("""
        SELECT pd.post.id
        FROM PostDislike pd
        WHERE pd.user.id = :userId
        ORDER BY pd.createdAt DESC
        """)
    List<Long> findDislikedPostIdsByUserId(@Param("userId") Long userId, Pageable pageable);
}
