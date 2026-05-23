package com.socialfeed.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ReputationPointDto {
    private LocalDateTime recordedAt;
    private double score;
}
