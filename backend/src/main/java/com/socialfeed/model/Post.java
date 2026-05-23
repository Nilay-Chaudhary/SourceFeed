package com.socialfeed.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "posts",
    indexes = {
        @Index(name = "idx_posts_user_created", columnList = "user_id, created_at DESC")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "parent_post_id")
    private Long parentPostId;

    @ElementCollection
    @CollectionTable(name = "post_sources", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "source_url", nullable = false, length = 1000)
    private List<String> sources = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "post_tags", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "topic_tag", nullable = false, length = 64)
    private List<String> topicTags = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_celebrity_post", nullable = false)
    private boolean isCelebrityPost = false;

    @Column(name = "post_trust_score", nullable = false)
    private Double postTrustScore = 0.0d;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_verdict", nullable = false, length = 32)
    private AiVerdict aiVerdict = AiVerdict.PROCESSING;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
