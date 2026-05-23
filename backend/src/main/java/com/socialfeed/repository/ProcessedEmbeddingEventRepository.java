package com.socialfeed.repository;

import com.socialfeed.model.ProcessedEmbeddingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEmbeddingEventRepository extends JpaRepository<ProcessedEmbeddingEvent, Long> {
    boolean existsByEventId(String eventId);
}