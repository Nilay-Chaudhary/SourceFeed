package com.socialfeed.dto;

import com.socialfeed.model.AiVerdict;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostVerificationDto {
    private Long postId;
    private AiVerdict verdict;
    private Double confidenceScore;
    private Double evidenceScore;
    private String explanation;
    private List<String> sourcesUsed;
    private LocalDateTime processedAt;
}
