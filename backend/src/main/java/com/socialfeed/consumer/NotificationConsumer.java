package com.socialfeed.consumer;

import com.socialfeed.event.NotificationEvent;
import com.socialfeed.service.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for notification events.
 * Persists notifications to the database asynchronously.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {
    
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;
    
    @KafkaListener(
        topics = "${kafka.topics.notifications:notifications}",
        groupId = "${spring.kafka.consumer.group-id:social-feed-group}",
        containerFactory = "notificationKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleNotificationEvent(NotificationEvent event, Acknowledgment acknowledgment) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            log.info("Received notification event: userId={}, type={}, relatedPostId={}, relatedUserId={}",
                event.getUserId(), event.getType(), event.getRelatedPostId(), event.getRelatedUserId());
            
            // Create and persist the notification
            notificationService.createNotification(
                event.getUserId(),
                event.getType(),
                event.getMessage(),
                event.getRelatedPostId(),
                event.getRelatedUserId()
            );
            
            sample.stop(Timer.builder("kafka_notification_processing_duration")
                .description("Duration from notification event receipt to persistence")
                .tag("topic", "notifications")
                .tag("status", "success")
                .register(meterRegistry));
            
            meterRegistry.counter("notifications_created_total", "type", event.getType().toString()).increment();
            
            acknowledgment.acknowledge();
            log.debug("Notification processed and acknowledged: userId={}", event.getUserId());
            
        } catch (Exception e) {
            sample.stop(Timer.builder("kafka_notification_processing_duration")
                .description("Duration from notification event receipt to persistence")
                .tag("topic", "notifications")
                .tag("status", "error")
                .register(meterRegistry));
            
            meterRegistry.counter("notifications_processing_errors_total", "type", "unknown").increment();
            log.error("Error processing notification event: {}", event, e);
            throw e; // Let Spring Kafka handle retry/dead-letter logic
        }
    }
}
