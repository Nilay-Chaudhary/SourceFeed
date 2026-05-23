package com.socialfeed.controller;

import com.socialfeed.dto.PostDto;
import com.socialfeed.dto.TimelineCursorPageDto;
import com.socialfeed.model.User;
import com.socialfeed.service.EmbeddingService;
import com.socialfeed.service.TimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/timeline")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService timelineService;
    private final EmbeddingService embeddingService;

    @GetMapping
    public ResponseEntity<List<PostDto>> getTimeline(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<PostDto> timeline = timelineService.getUserTimeline(currentUser.getId(), page, size);
        return ResponseEntity.ok(timeline);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<PostDto>> getUserTimeline(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<PostDto> timeline = timelineService.getUserTimeline(userId, page, size);
        return ResponseEntity.ok(timeline);
    }

    @GetMapping("/cursor")
    public ResponseEntity<TimelineCursorPageDto> getTimelineByCursor(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        TimelineCursorPageDto timeline = timelineService.getUserTimelineByCursor(currentUser.getId(), cursor, size);
        return ResponseEntity.ok(timeline);
    }

    @GetMapping("/{userId}/cursor")
    public ResponseEntity<TimelineCursorPageDto> getUserTimelineByCursor(
            @PathVariable Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        TimelineCursorPageDto timeline = timelineService.getUserTimelineByCursor(userId, cursor, size);
        return ResponseEntity.ok(timeline);
    }

    @GetMapping("/for-you")
    public ResponseEntity<List<PostDto>> getForYouTimeline(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<PostDto> ranked = embeddingService.getForYouPosts(currentUser.getId(), page, size);
        return ResponseEntity.ok(ranked);
    }
}
