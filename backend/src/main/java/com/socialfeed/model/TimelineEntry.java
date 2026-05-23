package com.socialfeed.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "timeline_entries", 
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "post_id"})
    },
    indexes = {
        @Index(name = "idx_timeline_user_post_created", columnList = "user_id, post_created_at DESC, post_id DESC")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "post_user_id", nullable = false)
    private Long postUserId;

    @Column(name = "post_content", nullable = false, length = 1000)
    private String postContent;

    @Column(name = "post_created_at", nullable = false)
    private LocalDateTime postCreatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
