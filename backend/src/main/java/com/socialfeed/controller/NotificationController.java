package com.socialfeed.controller;

import com.socialfeed.dto.NotificationDto;
import com.socialfeed.model.User;
import com.socialfeed.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for notification endpoints.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {
    
    private final NotificationService notificationService;
    
    /**
     * Get all notifications for the current user with pagination.
     * Query param 'page' defaults to 0.
     */
    @GetMapping
    public ResponseEntity<Page<NotificationDto>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal User currentUser
    ) {
        Page<NotificationDto> notificationsPage = notificationService
            .getAllNotifications(currentUser.getId(), page)
            .map(NotificationDto::fromEntity);
        return ResponseEntity.ok(notificationsPage);
    }
    
    /**
     * Get unread notifications for the current user.
     */
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationDto>> getUnreadNotifications(
            @AuthenticationPrincipal User currentUser
    ) {
        List<NotificationDto> unread = notificationService
            .getUnreadNotifications(currentUser.getId())
            .stream()
            .map(NotificationDto::fromEntity)
            .collect(Collectors.toList());
        return ResponseEntity.ok(unread);
    }
    
    /**
     * Get count of unread notifications for the current user.
     * Useful for displaying a badge.
     */
    @GetMapping("/unread/count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal User currentUser
    ) {
        Long count = notificationService.getUnreadCount(currentUser.getId());
        return ResponseEntity.ok(new UnreadCountResponse(count));
    }
    
    /**
     * Mark a single notification as read.
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser
    ) {
        notificationService.markAsRead(id, currentUser.getId());
        log.info("Marked notification {} as read by user {}", id, currentUser.getId());
        return ResponseEntity.ok().build();
    }
    
    /**
     * Mark all notifications as read for the current user.
     */
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal User currentUser
    ) {
        notificationService.markAllAsRead(currentUser.getId());
        log.info("Marked all notifications as read for user {}", currentUser.getId());
        return ResponseEntity.ok().build();
    }
    
    /**
     * Response DTO for unread count endpoint.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class UnreadCountResponse {
        private Long count;
    }
}
