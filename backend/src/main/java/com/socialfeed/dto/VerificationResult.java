package com.socialfeed.dto;

import com.socialfeed.model.AiVerdict;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResult {
    private AiVerdict verdict;
    private double confidenceScore;
    private double evidenceScore;
    private String explanation;
    private List<String> sourcesUsed;
}
