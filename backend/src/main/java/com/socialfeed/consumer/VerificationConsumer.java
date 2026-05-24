package com.socialfeed.consumer;

import com.socialfeed.event.PostCreatedEvent;
import com.socialfeed.service.VerificationAgent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class VerificationConsumer {

    private final VerificationAgent verificationAgent;
    private final MeterRegistry meterRegistry;

    @KafkaListener(
        topics = "${kafka.topics.post-created}",
        groupId = "${spring.kafka.consumer.verification-group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePostCreatedEvent(PostCreatedEvent event, Acknowledgment acknowledgment) {
        try {
            verificationAgent.verifyPost(event);
            meterRegistry.counter("verification_events_consumed_total", "status", "success").increment();
        } catch (Exception ex) {
            meterRegistry.counter("verification_events_consumed_total", "status", "failure").increment();
            log.error("Verification consumer failed for eventId={}, postId={}", event == null ? null : event.getEventId(), event == null ? null : event.getPostId(), ex);
        } finally {
            acknowledgment.acknowledge();
        }
    }
}
