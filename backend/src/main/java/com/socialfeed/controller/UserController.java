package com.socialfeed.controller;

import com.socialfeed.dto.UserDto;
import com.socialfeed.dto.UserProfileDto;
import com.socialfeed.dto.UserSearchDto;
import com.socialfeed.dto.TopicReputationDto;
import com.socialfeed.dto.UserReputationHistoryDto;
import com.socialfeed.model.User;
import com.socialfeed.service.EmbeddingService;
import com.socialfeed.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final EmbeddingService embeddingService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserDto> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<UserDto> getUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUserByUsername(username));
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchDto>> searchUsers(
            @AuthenticationPrincipal User currentUser,
            @RequestParam String query) {
        return ResponseEntity.ok(userService.searchUsersByUsername(query, currentUser.getId()));
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<UserProfileDto> getUserProfile(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserProfileById(userId, currentUser.getId()));
    }

    @GetMapping("/username/{username}/profile")
    public ResponseEntity<UserProfileDto> getUserProfileByUsername(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String username) {
        return ResponseEntity.ok(userService.getUserProfileByUsername(username, currentUser.getId()));
    }

    @PostMapping("/{userId}/follow")
    public ResponseEntity<Void> followUser(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long userId) {
        userService.followUser(currentUser.getId(), userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}/unfollow")
    public ResponseEntity<Void> unfollowUser(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long userId) {
        userService.unfollowUser(currentUser.getId(), userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{userId}/followers")
    public ResponseEntity<List<UserDto>> getFollowers(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getFollowers(userId));
    }

    @GetMapping("/{userId}/following")
    public ResponseEntity<List<UserDto>> getFollowing(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getFollowing(userId));
    }

    @GetMapping("/{userId}/topic-reputation")
    public ResponseEntity<List<TopicReputationDto>> getTopicReputation(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getTopicReputation(userId));
    }

    @GetMapping("/{userId}/reputation-history")
    public ResponseEntity<UserReputationHistoryDto> getReputationHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "80") int limit) {
        return ResponseEntity.ok(userService.getReputationHistory(userId, limit));
    }

    @PostMapping("/me/embedding/recompute")
    public ResponseEntity<Void> recomputeMyProfileEmbedding(@AuthenticationPrincipal User currentUser) {
        embeddingService.recomputeUserProfileEmbedding(currentUser.getId());
        return ResponseEntity.accepted().build();
    }
}
