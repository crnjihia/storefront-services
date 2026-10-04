package com.storefront.paymentservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Poll every 5 seconds (configurable via property outbox.poll-interval)
    @Scheduled(fixedDelayString = "${outbox.poll-interval:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            try {
                Object payloadObject = objectMapper.readValue(event.getPayload(), Object.class);
                String key = event.getAggregateId() != null ? event.getAggregateId().toString() : null;
                kafkaTemplate.send(event.getTopic(), key, payloadObject).addCallback(result -> {
                    event.setPublishedAt(Instant.now());
                    outboxEventRepository.save(event);
                }, ex -> {
                    log.error("Failed to publish outbox event {} to topic {}: {}", event.getId(), event.getTopic(), ex.getMessage());
                });
            } catch (Exception e) {
                log.error("Error processing outbox event {}: {}", event.getId(), e.getMessage(), e);
            }
        }
    }
}
