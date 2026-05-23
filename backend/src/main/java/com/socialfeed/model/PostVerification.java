package com.socialfeed.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "post_verifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false, unique = true)
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", nullable = false, length = 32)
    private AiVerdict verdict;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "evidence_score")
    private Double evidenceScore;

    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    @Column(name = "sources_used", columnDefinition = "text")
    private String sourcesUsed;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}