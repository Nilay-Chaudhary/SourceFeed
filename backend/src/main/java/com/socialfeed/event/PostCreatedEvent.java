package com.socialfeed.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostCreatedEvent {
    private Long postId;
    private Long parentPostId;
    private Long userId;
    private String content;
    private LocalDateTime createdAt;
    private String eventId; // For idempotency
    private boolean celebrity; // whether this post should skip fanout (celebrity/high-follower)
}
