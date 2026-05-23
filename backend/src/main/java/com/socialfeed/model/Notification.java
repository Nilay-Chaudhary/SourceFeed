package com.socialfeed.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Notification entity representing a notification sent to a user.
 * Notifications are triggered by events such as counterpoint creation, likes, and follows.
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notifications_user_id", columnList = "user_id"),
    @Index(name = "idx_notifications_user_read", columnList = "user_id,is_read"),
    @Index(name = "idx_notifications_created_at", columnList = "created_at DESC"),
    @Index(name = "idx_notifications_user_created", columnList = "user_id,created_at DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "notification_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private NotificationType type;
    
    @Column(name = "related_post_id")
    private Long relatedPostId;
    
    @Column(name = "related_user_id")
    private Long relatedUserId;
    
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;
    
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
    
    /**
     * Notification types that trigger different events in the system.
     */
    public enum NotificationType {
        COUNTERPOINT_CREATED,
        POST_LIKED,
        USER_FOLLOWED
    }
}
