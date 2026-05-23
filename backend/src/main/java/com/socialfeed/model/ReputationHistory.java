package com.socialfeed.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reputation_history", indexes = {
    @Index(name = "idx_reputation_history_user_recorded", columnList = "user_id, recorded_at"),
    @Index(name = "idx_reputation_history_user_topic_recorded", columnList = "user_id, topic_tag, recorded_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReputationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "topic_tag")
    private String topicTag;

    @Column(name = "reputation_score", nullable = false)
    private Double reputationScore;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
    }
}
