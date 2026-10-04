package com.storefront.catalogservice.listener;

import com.storefront.common.events.EventEnvelope;
import com.storefront.catalogservice.saga.CatalogSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCreatedListener {

    private final CatalogSagaOrchestrator orchestrator;

    @KafkaListener(topics = "order.created", groupId = "catalog-service-order-created")
    public void handle(ConsumerRecord<String, Object> record) {
        @SuppressWarnings("unchecked")
        EventEnvelope<Object> envelope = (EventEnvelope<Object>) record.value();
        if (envelope == null) {
            return;
        }
        // Forward the entire envelope to the saga orchestrator for processing.
        orchestrator.handleOrderCreated(envelope);
    }
}
