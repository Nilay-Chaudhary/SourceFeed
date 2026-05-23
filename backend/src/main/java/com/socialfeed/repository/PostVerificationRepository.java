package com.socialfeed.repository;

import com.socialfeed.model.Post;
import com.socialfeed.model.PostVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostVerificationRepository extends JpaRepository<PostVerification, Long> {
    Optional<PostVerification> findByPost(Post post);
    Optional<PostVerification> findByPostId(Long postId);
}