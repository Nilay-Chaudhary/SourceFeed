package com.socialfeed.event;

import com.socialfeed.model.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when a notification needs to be created.
 * This event is consumed asynchronously to persist notifications.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEvent {
    
    private Long userId;
    
    private Notification.NotificationType type;
    
    private String message;
    
    private Long relatedPostId;
    
    private Long relatedUserId;
    
    private Instant timestamp;
    
    @Override
    public String toString() {
        return "NotificationEvent{" +
                "userId=" + userId +
                ", type=" + type +
                ", message='" + message + '\'' +
                ", relatedPostId=" + relatedPostId +
                ", relatedUserId=" + relatedUserId +
                ", timestamp=" + timestamp +
                '}';
    }
}
