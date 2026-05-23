package com.socialfeed.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostDto {
    private Long id;
    private Long userId;
    private String username;
    private String displayName;
    private String content;
    private LocalDateTime createdAt;
    private Long parentPostId;
    private List<String> sources;
    private List<String> topicTags;
    private double postTrustScore;
    private String aiVerdict;
    private long likeCount;
    private boolean likedByMe;
    private long dislikeCount;
    private boolean dislikedByMe;
}
