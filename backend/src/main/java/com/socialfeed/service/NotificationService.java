package com.socialfeed.service;

import com.socialfeed.model.Notification;
import com.socialfeed.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service for managing user notifications.
 * Handles creation, retrieval, and marking of notifications as read.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    
    private static final int PAGE_SIZE = 20;
    private static final int RETENTION_DAYS = 30;
    
    /**
     * Create and persist a new notification.
     */
    @Transactional
    public Notification createNotification(
            Long userId,
            Notification.NotificationType type,
            String message,
            Long relatedPostId,
            Long relatedUserId
    ) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .message(message)
                .relatedPostId(relatedPostId)
                .relatedUserId(relatedUserId)
                .isRead(false)
                .createdAt(Instant.now())
                .build();
        
        notification = notificationRepository.save(notification);
        log.info("Created notification for user {} of type {}", userId, type);
        return notification;
    }
    
    /**
     * Get unread notifications for a user.
     */
    @Transactional(readOnly = true)
    public List<Notification> getUnreadNotifications(Long userId) {
        return notificationRepository.findUnreadByUserId(userId);
    }
    
    /**
     * Get all notifications for a user with pagination.
     */
    @Transactional(readOnly = true)
    public Page<Notification> getAllNotifications(Long userId, int page) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
    
    /**
     * Get unread notifications for a user with pagination.
     */
    @Transactional(readOnly = true)
    public Page<Notification> getUnreadNotifications(Long userId, int page) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE);
        return notificationRepository.findUnreadByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
    
    /**
     * Get count of unread notifications for a user.
     */
    @Transactional(readOnly = true)
    public Long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }
    
    /**
     * Mark a single notification as read.
     */
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        notificationRepository.markAsReadByIdAndUserId(notificationId, userId);
        log.debug("Marked notification {} as read for user {}", notificationId, userId);
    }
    
    /**
     * Mark all notifications for a user as read.
     */
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
        log.info("Marked all notifications as read for user {}", userId);
    }
    
    /**
     * Delete old notifications beyond retention period.
     * Typically called by a scheduled task.
     */
    @Transactional
    public void cleanupOldNotifications(Long userId) {
        Instant cutoffDate = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        notificationRepository.deleteOldNotifications(userId, cutoffDate);
        log.info("Cleaned up old notifications for user {} (before {})", userId, cutoffDate);
    }
}
