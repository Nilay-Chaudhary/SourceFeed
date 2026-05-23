package com.socialfeed.service;

import com.socialfeed.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrustAggregateService {

    private final UserRepository userRepository;
    private final ReputationHistoryService reputationHistoryService;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void recordPostCreated(Long userId, double initialPostTrustScore) {
        applyDelta("create_post", userId, initialPostTrustScore, 1L);
    }

    @Transactional
    public void recordPostTrustDelta(Long userId, double trustDelta) {
        applyDelta("trust_delta", userId, trustDelta, 0L);
    }

    private void applyDelta(String action, Long userId, double sumDelta, long countDelta) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            int updatedRows = userRepository.updateTrustAggregate(userId, sumDelta, countDelta);
            if (updatedRows == 0) {
                sample.stop(Timer.builder("trust_recompute_duration")
                    .description("Duration of trust aggregate recomputation")
                    .tag("action", action)
                    .tag("status", "failure")
                    .register(meterRegistry));
                throw new RuntimeException("User not found");
            }

            double currentScore = userRepository.findById(userId)
                .map(user -> user.getUserTrustScore() == null ? 1.0d : user.getUserTrustScore())
                .orElse(1.0d);
            reputationHistoryService.recordOverallSnapshot(userId, currentScore);

            sample.stop(Timer.builder("trust_recompute_duration")
                .description("Duration of trust aggregate recomputation")
                .tag("action", action)
                .tag("status", "success")
                .register(meterRegistry));
        } catch (RuntimeException ex) {
            if (!ex.getMessage().equals("User not found")) {
                sample.stop(Timer.builder("trust_recompute_duration")
                    .description("Duration of trust aggregate recomputation")
                    .tag("action", action)
                    .tag("status", "failure")
                    .register(meterRegistry));
            }
            throw ex;
        }
    }
}