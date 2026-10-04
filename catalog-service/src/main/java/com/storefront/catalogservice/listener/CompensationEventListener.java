package com.storefront.catalogservice.listener;

import com.storefront.catalogservice.saga.CatalogSagaOrchestrator;
import com.storefront.common.events.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompensationEventListener {

    private final CatalogSagaOrchestrator orchestrator;

    // Listen to commands that indicate a reservation should be released (e.g., payment failure or order cancellation)
    @KafkaListener(topics = {"payment.failed", "order.cancelled"}, groupId = "catalog-service-compensation")
    public void handle(ConsumerRecord<String, Object> record) {
        @SuppressWarnings("unchecked")
        EventEnvelope<Object> envelope = (EventEnvelope<Object>) record.value();
        if (envelope == null) {
            return;
        }
        // The orderId is the aggregate identifier for the reservation.
        orchestrator.handleRelease(envelope.orderId());
    }
}
