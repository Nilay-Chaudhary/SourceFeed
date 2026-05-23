package com.socialfeed.controller;

import com.socialfeed.dto.CreatePostRequest;
import com.socialfeed.dto.LikeStatusDto;
import com.socialfeed.dto.PostDto;
import com.socialfeed.dto.PostVerificationDto;
import com.socialfeed.dto.PostSearchResultDto;
import com.socialfeed.model.User;
import com.socialfeed.service.PostService;
import com.socialfeed.service.EmbeddingService;
import com.socialfeed.service.VerificationAgent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final EmbeddingService embeddingService;
    private final VerificationAgent verificationAgent;

    @PostMapping
    public ResponseEntity<PostDto> createPost(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreatePostRequest request) {
        PostDto post = postService.createPost(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(post);
    }

    @PostMapping("/{postId}/counterpoints")
    public ResponseEntity<PostDto> createCounterpoint(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long postId,
            @Valid @RequestBody CreatePostRequest request) {
        PostDto counterpoint = postService.createCounterpoint(postId, currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(counterpoint);
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostDto> getPost(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.getPostById(postId));
    }

    @GetMapping("/{postId}/verification")
    public ResponseEntity<PostVerificationDto> getPostVerification(@PathVariable Long postId) {
        return verificationAgent.getVerification(postId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{postId}/counterpoints")
    public ResponseEntity<List<PostDto>> getCounterpoints(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long postId) {
        return ResponseEntity.ok(postService.getCounterpoints(postId, currentUser.getId()));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostDto>> getUserPosts(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long userId) {
        return ResponseEntity.ok(postService.getUserPosts(userId, currentUser.getId()));
    }

    @GetMapping("/user/{userId}/page")
    public ResponseEntity<?> getUserPostsPage(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(postService.getUserPostsPage(userId, currentUser.getId(), page, size));
    }

    @PostMapping("/{postId}/likes")
    public ResponseEntity<LikeStatusDto> likePost(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long postId) {
        return ResponseEntity.ok(postService.likePost(postId, currentUser.getId()));
    }

    @DeleteMapping("/{postId}/likes")
    public ResponseEntity<LikeStatusDto> unlikePost(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long postId) {
        return ResponseEntity.ok(postService.unlikePost(postId, currentUser.getId()));
    }

    @PostMapping("/{postId}/dislikes")
    public ResponseEntity<LikeStatusDto> dislikePost(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long postId) {
        return ResponseEntity.ok(postService.dislikePost(postId, currentUser.getId()));
    }

    @DeleteMapping("/{postId}/dislikes")
    public ResponseEntity<LikeStatusDto> undislikePost(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long postId) {
        return ResponseEntity.ok(postService.undislikePost(postId, currentUser.getId()));
    }

    @GetMapping("/search")
    public ResponseEntity<List<PostSearchResultDto>> searchPosts(
            @AuthenticationPrincipal User currentUser,
            @RequestParam String query,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(embeddingService.searchPosts(query, limit, currentUser.getId()));
    }

    @GetMapping("/topic-tags")
    public ResponseEntity<List<String>> getSupportedTopicTags() {
        return ResponseEntity.ok(postService.getSupportedTopicTags());
    }
}
