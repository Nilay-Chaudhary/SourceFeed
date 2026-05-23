package com.socialfeed.repository;

import com.socialfeed.model.PostEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostEmbeddingRepository extends JpaRepository<PostEmbedding, Long> {
    Optional<PostEmbedding> findByPostId(Long postId);
    List<PostEmbedding> findByPostIdIn(List<Long> postIds);
}