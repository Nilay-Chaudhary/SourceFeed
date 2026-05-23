package com.socialfeed.repository;

import com.socialfeed.model.UserTopicReputation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTopicReputationRepository extends JpaRepository<UserTopicReputation, Long> {

    List<UserTopicReputation> findByUserIdAndTopicTagIn(Long userId, List<String> topicTags);

    Optional<UserTopicReputation> findByUserIdAndTopicTag(Long userId, String topicTag);

    List<UserTopicReputation> findByUserIdOrderByTopicTagAsc(Long userId);

    @Modifying
    @Query(value = """
        INSERT INTO user_topic_reputation (
            user_id,
            topic_tag,
            topic_trust_sum,
            topic_trust_count,
            topic_trust_score,
            created_at,
            updated_at
        )
        VALUES (
            :userId,
            :topicTag,
            :sumDelta,
            :countDelta,
            CASE
                WHEN :countDelta <= 0 THEN 1.0
                ELSE :sumDelta / :countDelta
            END,
            NOW(),
            NOW()
        )
        ON CONFLICT (user_id, topic_tag) DO UPDATE
        SET
            topic_trust_sum = user_topic_reputation.topic_trust_sum + :sumDelta,
            topic_trust_count = user_topic_reputation.topic_trust_count + :countDelta,
            topic_trust_score = CASE
                WHEN user_topic_reputation.topic_trust_count + :countDelta <= 0 THEN 1.0
                ELSE (user_topic_reputation.topic_trust_sum + :sumDelta) / (user_topic_reputation.topic_trust_count + :countDelta)
            END,
            updated_at = NOW()
        """, nativeQuery = true)
    int upsertTopicTrustAggregate(
        @Param("userId") Long userId,
        @Param("topicTag") String topicTag,
        @Param("sumDelta") double sumDelta,
        @Param("countDelta") long countDelta
    );
}
