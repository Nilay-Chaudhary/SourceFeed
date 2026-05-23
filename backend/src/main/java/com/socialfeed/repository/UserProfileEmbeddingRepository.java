package com.socialfeed.repository;

import com.socialfeed.model.UserProfileEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileEmbeddingRepository extends JpaRepository<UserProfileEmbedding, Long> {
    Optional<UserProfileEmbedding> findByUserId(Long userId);
}