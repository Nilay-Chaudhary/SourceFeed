package com.socialfeed.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UserProfileDto {
    private Long id;
    private String username;
    private String displayName;
    private Double userTrustScore;
    private boolean me;
    private boolean following;
    private long followerCount;
    private long followingCount;
    private long postCount;
    private List<TopicReputationDto> topicReputations;
}
