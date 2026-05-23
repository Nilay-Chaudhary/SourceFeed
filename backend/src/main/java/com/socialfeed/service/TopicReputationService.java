package com.socialfeed.service;

import com.socialfeed.dto.TopicReputationDto;
import com.socialfeed.model.UserTopicReputation;
import com.socialfeed.repository.UserTopicReputationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TopicReputationService {

    private final UserTopicReputationRepository userTopicReputationRepository;
    private final ReputationHistoryService reputationHistoryService;

    @Transactional
    public void recordReactionFeedbackForTopics(Long userId, List<String> topicTags, double trustDelta, long countDelta) {
        applyDelta(userId, topicTags, trustDelta, countDelta);
    }

    @Transactional(readOnly = true)
    public double resolveTrustForTopics(Long userId, List<String> topicTags, double fallbackTrust) {
        if (topicTags == null || topicTags.isEmpty()) {
            return Math.max(0.0d, fallbackTrust);
        }

        List<UserTopicReputation> reputations = userTopicReputationRepository.findByUserIdAndTopicTagIn(userId, topicTags);
        if (reputations.isEmpty()) {
            return Math.max(0.0d, fallbackTrust);
        }

        double topicAverage = reputations.stream()
            .map(UserTopicReputation::getTopicTrustScore)
            .filter(score -> score != null)
            .mapToDouble(score -> Math.max(0.0d, score))
            .average()
            .orElse(Math.max(0.0d, fallbackTrust));

        return Math.max(0.0d, (fallbackTrust * 0.35d) + (topicAverage * 0.65d));
    }

    @Transactional(readOnly = true)
    public List<TopicReputationDto> getTopicReputationForUser(Long userId) {
        return userTopicReputationRepository.findByUserIdOrderByTopicTagAsc(userId)
            .stream()
            .map(rep -> new TopicReputationDto(
                rep.getTopicTag(),
                rep.getTopicTrustScore() == null ? 1.0d : rep.getTopicTrustScore(),
                rep.getTopicTrustSum() == null ? 0.0d : rep.getTopicTrustSum(),
                rep.getTopicTrustCount() == null ? 0L : rep.getTopicTrustCount()
            ))
            .toList();
    }

    private void applyDelta(Long userId, List<String> topicTags, double sumDelta, long countDelta) {
        if (topicTags == null || topicTags.isEmpty()) {
            return;
        }

        for (String topicTag : topicTags) {
            userTopicReputationRepository.upsertTopicTrustAggregate(userId, topicTag, sumDelta, countDelta);
            userTopicReputationRepository.findByUserIdAndTopicTag(userId, topicTag)
                .ifPresent(rep -> reputationHistoryService.recordTopicSnapshot(
                    userId,
                    topicTag,
                    rep.getTopicTrustScore() == null ? 1.0d : rep.getTopicTrustScore()
                ));
        }
    }
}
