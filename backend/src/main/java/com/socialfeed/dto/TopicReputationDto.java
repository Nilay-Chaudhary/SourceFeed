package com.socialfeed.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TopicReputationDto {
    private String topicTag;
    private double topicTrustScore;
    private double topicTrustSum;
    private long topicTrustCount;
}
