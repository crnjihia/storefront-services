package com.storefront.paymentservice.messaging;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class IdempotentConsumerHelper {

    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void process(String consumer, UUID eventId, Runnable handler) {
        boolean alreadyProcessed = processedEventRepository.existsByIdEventIdAndIdConsumer(eventId, consumer);
        if (alreadyProcessed) {
            return; // idempotent skip
        }
        handler.run();
        ProcessedEventId pk = new ProcessedEventId(eventId, consumer);
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .id(pk)
                .processedAt(java.time.Instant.now())
                .build();
        processedEventRepository.save(processedEvent);
    }
}
