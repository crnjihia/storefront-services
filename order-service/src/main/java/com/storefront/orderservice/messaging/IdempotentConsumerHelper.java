package com.storefront.orderservice.messaging;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class IdempotentConsumerHelper {

    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void process(String consumer, UUID eventId, Runnable handler) {
        // Check if the event has already been processed
        boolean alreadyProcessed = processedEventRepository.existsByIdEventIdAndIdConsumer(eventId, consumer);
        if (alreadyProcessed) {
            // Idempotent – skip processing
            return;
        }
        // Execute the handler logic
        handler.run();
        // Record the processing
        ProcessedEventId pk = new ProcessedEventId(eventId, consumer);
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .id(pk)
                .processedAt(java.time.Instant.now())
                .build();
        processedEventRepository.save(processedEvent);
    }
}
