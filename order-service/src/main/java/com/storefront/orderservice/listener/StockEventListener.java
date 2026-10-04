package com.storefront.orderservice.listener;

import com.storefront.common.events.EventEnvelope;
import com.storefront.orderservice.saga.OrderSagaOrchestrator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final OrderSagaOrchestrator orchestrator;

    @KafkaListener(topics = {"stock.reserved", "stock.reservation-failed"}, groupId = "order-service-stock")
    public void handle(ConsumerRecord<String, Object> record) {
        @SuppressWarnings("unchecked")
        EventEnvelope<Object> envelope = (EventEnvelope<Object>) record.value();
        if (envelope == null) {
            return;
        }
        String topic = record.topic();
        orchestrator.handleEvent(envelope.eventId(), topic, envelope);
    }
}
