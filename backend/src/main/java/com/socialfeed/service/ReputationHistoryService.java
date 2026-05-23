package com.socialfeed.service;

import com.socialfeed.dto.ReputationPointDto;
import com.socialfeed.dto.TopicReputationSeriesDto;
import com.socialfeed.dto.UserReputationHistoryDto;
import com.socialfeed.model.ReputationHistory;
import com.socialfeed.repository.ReputationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReputationHistoryService {

    private final ReputationHistoryRepository reputationHistoryRepository;

    @Transactional
    public void recordOverallSnapshot(Long userId, double score) {
        ReputationHistory entry = new ReputationHistory();
        entry.setUserId(userId);
        entry.setTopicTag(null);
        entry.setReputationScore(Math.max(0.0d, score));
        reputationHistoryRepository.save(entry);
    }

    @Transactional
    public void recordTopicSnapshot(Long userId, String topicTag, double score) {
        if (topicTag == null || topicTag.isBlank()) {
            return;
        }
        ReputationHistory entry = new ReputationHistory();
        entry.setUserId(userId);
        entry.setTopicTag(topicTag);
        entry.setReputationScore(Math.max(0.0d, score));
        reputationHistoryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public UserReputationHistoryDto getUserHistory(Long userId, int limitPerSeries) {
        int safeLimit = Math.max(10, Math.min(limitPerSeries, 300));
        List<ReputationHistory> history = reputationHistoryRepository.findByUserIdOrderByRecordedAtAsc(userId);

        List<ReputationPointDto> overall = new ArrayList<>();
        Map<String, List<ReputationPointDto>> topicSeries = new LinkedHashMap<>();

        for (ReputationHistory entry : history) {
            ReputationPointDto point = new ReputationPointDto(entry.getRecordedAt(), entry.getReputationScore() == null ? 0.0d : entry.getReputationScore());
            if (entry.getTopicTag() == null || entry.getTopicTag().isBlank()) {
                overall.add(point);
            } else {
                topicSeries.computeIfAbsent(entry.getTopicTag(), key -> new ArrayList<>()).add(point);
            }
        }

        List<ReputationPointDto> overallTrimmed = tail(overall, safeLimit);
        List<TopicReputationSeriesDto> topics = topicSeries.entrySet().stream()
            .map(entry -> new TopicReputationSeriesDto(entry.getKey(), tail(entry.getValue(), safeLimit)))
            .toList();

        return new UserReputationHistoryDto(overallTrimmed, topics);
    }

    private List<ReputationPointDto> tail(List<ReputationPointDto> source, int limit) {
        if (source.size() <= limit) {
            return source;
        }
        return new ArrayList<>(source.subList(source.size() - limit, source.size()));
    }
}
