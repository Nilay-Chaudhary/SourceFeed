package com.socialfeed.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TopicReputationSeriesDto {
    private String topicTag;
    private List<ReputationPointDto> points;
}
