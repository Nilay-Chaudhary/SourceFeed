package com.socialfeed.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LikeStatusDto {
    private Long postId;
    private long likeCount;
    private boolean likedByMe;
    private long dislikeCount;
    private boolean dislikedByMe;
}