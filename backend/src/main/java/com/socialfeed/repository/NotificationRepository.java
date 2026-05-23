package com.socialfeed.repository;

import com.socialfeed.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Repository for Notification entity.
 * Provides query methods for managing user notifications.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    /**
     * Find all unread notifications for a user ordered by creation date (newest first).
     */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.isRead = false ORDER BY n.createdAt DESC")
    List<Notification> findUnreadByUserId(@Param("userId") Long userId);
    
    /**
     * Find all notifications for a user (paginated) ordered by creation date (newest first).
     */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId ORDER BY n.createdAt DESC")
    Page<Notification> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * Find all unread notifications for a user (paginated).
     */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.isRead = false ORDER BY n.createdAt DESC")
    Page<Notification> findUnreadByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * Count unread notifications for a user.
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.isRead = false")
    Long countUnreadByUserId(@Param("userId") Long userId);
    
    /**
     * Mark a single notification as read.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id")
    void markAsRead(@Param("id") Long id);

    /**
     * Mark a single notification as read for a specific user.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id AND n.userId = :userId")
    void markAsReadByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
    
    /**
     * Mark all notifications for a user as read.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    void markAllAsReadByUserId(@Param("userId") Long userId);
    
    /**
     * Delete notifications older than a specified date for a user.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.userId = :userId AND n.createdAt < :beforeDate")
    void deleteOldNotifications(@Param("userId") Long userId, @Param("beforeDate") Instant beforeDate);
}
