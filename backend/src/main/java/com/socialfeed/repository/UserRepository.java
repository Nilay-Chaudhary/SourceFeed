package com.socialfeed.repository;

import com.socialfeed.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findByIdIn(List<Long> ids);
    List<User> findTop10ByUsernameContainingIgnoreCaseOrderByUsernameAsc(String query);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Modifying
    @Query(value = """
        UPDATE users
        SET post_trust_sum = COALESCE(post_trust_sum, 0) + :sumDelta,
            post_trust_count = COALESCE(post_trust_count, 0) + :countDelta,
            user_trust_score = CASE
                WHEN COALESCE(post_trust_count, 0) + :countDelta <= 0 THEN 1.0
                ELSE (COALESCE(post_trust_sum, 0) + :sumDelta) / (COALESCE(post_trust_count, 0) + :countDelta)
            END
        WHERE id = :userId
        """, nativeQuery = true)
    int updateTrustAggregate(@Param("userId") Long userId, @Param("sumDelta") double sumDelta, @Param("countDelta") long countDelta);
}
