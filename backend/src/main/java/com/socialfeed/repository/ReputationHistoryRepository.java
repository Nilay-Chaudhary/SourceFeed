package com.socialfeed.repository;

import com.socialfeed.model.ReputationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReputationHistoryRepository extends JpaRepository<ReputationHistory, Long> {
    List<ReputationHistory> findByUserIdOrderByRecordedAtAsc(Long userId);
}
