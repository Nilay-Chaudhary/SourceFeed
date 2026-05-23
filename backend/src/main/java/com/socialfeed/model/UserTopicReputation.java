package com.socialfeed.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "user_topic_reputation",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "topic_tag"})
    },
    indexes = {
        @Index(name = "idx_user_topic_reputation_user", columnList = "user_id"),
        @Index(name = "idx_user_topic_reputation_topic", columnList = "topic_tag")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserTopicReputation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "topic_tag", nullable = false, length = 64)
    private String topicTag;

    @Column(name = "topic_trust_score", nullable = false)
    private Double topicTrustScore;

    @Column(name = "topic_trust_sum", nullable = false)
    private Double topicTrustSum;

    @Column(name = "topic_trust_count", nullable = false)
    private Long topicTrustCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (topicTrustScore == null) {
            topicTrustScore = 1.0d;
        }
        if (topicTrustSum == null) {
            topicTrustSum = 0.0d;
        }
        if (topicTrustCount == null) {
            topicTrustCount = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
