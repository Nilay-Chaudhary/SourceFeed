package com.socialfeed.consumer;

import com.socialfeed.event.PostCreatedEvent;
import com.socialfeed.model.ProcessedEmbeddingEvent;
import com.socialfeed.repository.ProcessedEmbeddingEventRepository;
import com.socialfeed.service.EmbeddingService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostEmbeddingsConsumer {

    private final EmbeddingService embeddingService;
    private final ProcessedEmbeddingEventRepository processedEmbeddingEventRepository;
    private final MeterRegistry meterRegistry;

    @KafkaListener(
        topics = "${kafka.topics.post-created}",
        groupId = "${spring.kafka.consumer.embeddings-group-id}",
        containerFactory = "embeddingsKafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePostCreatedEvent(PostCreatedEvent event, Acknowledgment acknowledgment) {
        try {
            log.info("Received post for embedding generation: eventId={}, postId={}, userId={}", event.getEventId(), event.getPostId(), event.getUserId());

            if (processedEmbeddingEventRepository.existsByEventId(event.getEventId())) {
                meterRegistry.counter("post_embeddings_total", "status", "duplicate").increment();
                acknowledgment.acknowledge();
                return;
            }

            embeddingService.enrichPost(event);

            ProcessedEmbeddingEvent processedEvent = new ProcessedEmbeddingEvent();
            processedEvent.setEventId(event.getEventId());
            try {
                processedEmbeddingEventRepository.save(processedEvent);
            } catch (DataIntegrityViolationException ex) {
                meterRegistry.counter("post_embeddings_total", "status", "duplicate").increment();
                acknowledgment.acknowledge();
                return;
            }

            meterRegistry.counter("post_embeddings_total", "status", "success").increment();
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            meterRegistry.counter("post_embeddings_total", "status", "error").increment();
            log.error("Failed to generate embedding: eventId={}, postId={}, userId={}", event.getEventId(), event.getPostId(), event.getUserId(), ex);
            throw ex;
        }
    }
}