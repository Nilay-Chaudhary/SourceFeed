package com.socialfeed.consumer;

import com.socialfeed.event.PostCreatedEvent;
import com.socialfeed.model.ProcessedEvent;
import com.socialfeed.repository.FollowRepository;
import com.socialfeed.repository.ProcessedEventRepository;
import com.socialfeed.service.TimelineService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostCreatedConsumer {

    private final TimelineService timelineService;
    private final FollowRepository followRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final MeterRegistry meterRegistry;

    @KafkaListener(
        topics = "${kafka.topics.post-created}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePostCreatedEvent(PostCreatedEvent event, Acknowledgment acknowledgment) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            log.info("Received post created event: eventId={}, postId={}, parentPostId={}, userId={}", event.getEventId(), event.getPostId(), event.getParentPostId(), event.getUserId());

            // Check if event already processed (idempotency)
            if (processedEventRepository.existsByEventId(event.getEventId())) {
                sample.stop(Timer.builder("kafka_event_processing_duration")
                    .description("Duration from event receipt to processing completion")
                    .tag("topic", "post-created-topic")
                    .tag("status", "duplicate")
                    .register(meterRegistry));
                log.info("Event already processed, skipping: eventId={}, postId={}", event.getEventId(), event.getPostId());
                meterRegistry.counter("timelines_materialized_total", "status", "duplicated", "followerCount", "0").increment();
                acknowledgment.acknowledge();
                return;
            }

            // Counterpoints are child posts and should not be materialized in timeline feeds.
            if (event.getParentPostId() != null) {
                ProcessedEvent processedEvent = new ProcessedEvent();
                processedEvent.setEventId(event.getEventId());
                try {
                    processedEventRepository.save(processedEvent);
                } catch (DataIntegrityViolationException ex) {
                    sample.stop(Timer.builder("kafka_event_processing_duration")
                        .description("Duration from event receipt to processing completion")
                        .tag("topic", "post-created-topic")
                        .tag("status", "duplicate")
                        .register(meterRegistry));
                    log.info("Counterpoint event already processed due to concurrent write, skipping: eventId={}, postId={}", event.getEventId(), event.getPostId());
                    meterRegistry.counter("timelines_materialized_total", "status", "duplicated", "followerCount", "0").increment();
                    acknowledgment.acknowledge();
                    return;
                }

                sample.stop(Timer.builder("kafka_event_processing_duration")
                    .description("Duration from event receipt to processing completion")
                    .tag("topic", "post-created-topic")
                    .tag("status", "success")
                    .register(meterRegistry));
                log.info("Skipping timeline materialization for counterpoint: eventId={}, postId={}, parentPostId={}, userId={}",
                    event.getEventId(), event.getPostId(), event.getParentPostId(), event.getUserId());
                meterRegistry.counter("timelines_materialized_total", "status", "skipped_counterpoint", "followerCount", "0").increment();
                acknowledgment.acknowledge();
                return;
            }

            // Get all followers of the user who created the post
            List<Long> followerIds = followRepository.findFollowerIdsByFolloweeId(event.getUserId());
            String followerCountBucket = bucketing(followerIds.size());
            log.info("Materializing post to timelines: eventId={}, postId={}, authorId={}, followerCount={}", event.getEventId(), event.getPostId(), event.getUserId(), followerIds.size());
            
            // Add post to the author's own timeline
            timelineService.addToTimeline(
                event.getUserId(),
                event.getPostId(),
                event.getUserId(),
                event.getContent(),
                event.getCreatedAt()
            );

            // If this is a celebrity/high-follower post, skip fanout to followers
            if (event.isCelebrity()) {
                log.info("Skipping fanout for celebrity post: eventId={}, postId={}, authorId={}", event.getEventId(), event.getPostId(), event.getUserId());
                // Mark event as processed (author already has entry)
                ProcessedEvent processedEvent = new ProcessedEvent();
                processedEvent.setEventId(event.getEventId());
                try {
                    processedEventRepository.save(processedEvent);
                } catch (DataIntegrityViolationException ex) {
                    sample.stop(Timer.builder("kafka_event_processing_duration")
                        .description("Duration from event receipt to processing completion")
                        .tag("topic", "post-created-topic")
                        .tag("status", "duplicate")
                        .register(meterRegistry));
                    log.info("Event already processed due to concurrent write, skipping: eventId={}, postId={}", event.getEventId(), event.getPostId());
                    meterRegistry.counter("timelines_materialized_total", "status", "duplicated", "followerCount", followerCountBucket).increment();
                    acknowledgment.acknowledge();
                    return;
                }

                sample.stop(Timer.builder("kafka_event_processing_duration")
                    .description("Duration from event receipt to processing completion")
                    .tag("topic", "post-created-topic")
                    .tag("status", "success")
                    .register(meterRegistry));
                log.info("Celebrity post handled (fanout skipped): eventId={}, postId={}, authorId={}", event.getEventId(), event.getPostId(), event.getUserId());
                meterRegistry.counter("timelines_materialized_total", "status", "skipped_fanout", "followerCount", followerCountBucket).increment();
                acknowledgment.acknowledge();
                return;
            }

            // Batch insert timeline entries for followers
            if (!followerIds.isEmpty()) {
                timelineService.addToTimelineBatch(followerIds, event.getPostId(), event.getUserId(), event.getContent(), event.getCreatedAt());
            }

            // Mark event as processed
            ProcessedEvent processedEvent = new ProcessedEvent();
            processedEvent.setEventId(event.getEventId());
            try {
                processedEventRepository.save(processedEvent);
            } catch (DataIntegrityViolationException ex) {
                sample.stop(Timer.builder("kafka_event_processing_duration")
                    .description("Duration from event receipt to processing completion")
                    .tag("topic", "post-created-topic")
                    .tag("status", "duplicate")
                    .register(meterRegistry));
                log.info("Event already processed due to concurrent write, skipping: eventId={}, postId={}", event.getEventId(), event.getPostId());
                meterRegistry.counter("timelines_materialized_total", "status", "duplicated", "followerCount", followerCountBucket).increment();
                acknowledgment.acknowledge();
                return;
            }

            sample.stop(Timer.builder("kafka_event_processing_duration")
                .description("Duration from event receipt to processing completion")
                .tag("topic", "post-created-topic")
                .tag("status", "success")
                .register(meterRegistry));
            log.info("Post materialized successfully: eventId={}, postId={}, authorId={}, totalTimelines={}", event.getEventId(), event.getPostId(), event.getUserId(), followerIds.size() + 1);
            meterRegistry.counter("timelines_materialized_total", "status", "success", "followerCount", followerCountBucket).increment();

            // Manually acknowledge the message
            acknowledgment.acknowledge();
        } catch (Exception e) {
            sample.stop(Timer.builder("kafka_event_processing_duration")
                .description("Duration from event receipt to processing completion")
                .tag("topic", "post-created-topic")
                .tag("status", "error")
                .register(meterRegistry));
            log.error("Error processing post created event: eventId={}, postId={}, userId={}", event.getEventId(), event.getPostId(), event.getUserId(), e);
            meterRegistry.counter("timelines_materialized_total", "status", "error").increment();
            // Don't acknowledge - message will be redelivered
            throw e;
        }
    }

    private String bucketing(int count) {
        if (count == 0) return "0";
        if (count <= 10) return "1-10";
        if (count <= 100) return "11-100";
        if (count <= 1000) return "101-1k";
        return "1k+";
    }
}
