package com.socialfeed.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "post_dislikes",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"post_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_post_dislikes_post", columnList = "post_id"),
        @Index(name = "idx_post_dislikes_user", columnList = "user_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostDislike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "trust_contribution", nullable = false)
    private Double trustContribution;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (trustContribution == null) {
            trustContribution = 0.0d;
        }
    }
}
