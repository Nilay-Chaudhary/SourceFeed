package com.socialfeed.dto;

import com.socialfeed.model.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for representing a notification in API responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDto {
    
    private Long id;
    
    private Long userId;
    
    private String type; // NotificationType enum as string
    
    private String message;
    
    private Long relatedPostId;
    
    private Long relatedUserId;
    
    private Boolean isRead;
    
    private Instant createdAt;
    
    /**
     * Convert a Notification entity to a NotificationDto.
     */
    public static NotificationDto fromEntity(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .type(notification.getType().toString())
                .message(notification.getMessage())
                .relatedPostId(notification.getRelatedPostId())
                .relatedUserId(notification.getRelatedUserId())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
