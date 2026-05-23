package com.socialfeed.consumer;

import com.socialfeed.event.PostCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeadLetterTopicListener {

    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(
        topics = "${kafka.topics.post-created-dlt}",
        groupId = "${spring.kafka.consumer.group-id}-dlt"
    )
    public void handleDeadLetterMessage(ConsumerRecord<String, ?> record,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                         @Header(KafkaHeaders.OFFSET) long offset) {

        meterRegistry.counter("dlt_events_received_total", "topic", topic).increment();

        Object value = record.value();
        String payloadString = "<unserializable>";
        try {
            payloadString = objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            payloadString = String.valueOf(value);
        }

        Long postId = null;
        String eventId = null;
        Long userId = null;

        try {
            if (value instanceof java.util.Map) {
                java.util.Map map = (java.util.Map) value;
                Object p = map.get("postId");
                Object e = map.get("eventId");
                Object u = map.get("userId");
                postId = p == null ? null : Long.valueOf(String.valueOf(p));
                eventId = e == null ? null : String.valueOf(e);
                userId = u == null ? null : Long.valueOf(String.valueOf(u));
            } else if (value instanceof PostCreatedEvent) {
                PostCreatedEvent evt = (PostCreatedEvent) value;
                postId = evt.getPostId();
                eventId = evt.getEventId();
                userId = evt.getUserId();
            }
        } catch (Exception ex) {
            log.warn("Failed to extract fields from DLT payload, falling back to raw payload logging", ex);
        }

        log.error(
            "Dead letter message received from topic:{}, partition:{}, offset:{}. " +
            "Parsed Event: postId={}, eventId={}, userId={}. Raw payload={}. " +
            "This message failed processing and should be investigated.",
            topic, partition, offset,
            postId, eventId, userId, payloadString
        );

    }
}
