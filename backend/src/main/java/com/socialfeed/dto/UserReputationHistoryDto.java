package com.socialfeed.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UserReputationHistoryDto {
    private List<ReputationPointDto> overall;
    private List<TopicReputationSeriesDto> topics;
}
