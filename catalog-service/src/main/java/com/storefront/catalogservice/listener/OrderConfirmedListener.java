package com.storefront.catalogservice.listener;

import com.storefront.catalogservice.saga.CatalogSagaOrchestrator;
import com.storefront.common.events.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderConfirmedListener {

    private final CatalogSagaOrchestrator orchestrator;

    @KafkaListener(topics = "order.confirmed", groupId = "catalog-service-order-confirmed")
    public void handle(ConsumerRecord<String, Object> record) {
        @SuppressWarnings("unchecked")
        EventEnvelope<Object> envelope = (EventEnvelope<Object>) record.value();
        if (envelope == null) {
            return;
        }
        orchestrator.handleOrderConfirmed(envelope.orderId());
    }
}
