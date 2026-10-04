package com.storefront.orderservice.messaging;

import com.storefront.common.events.EventEnvelope;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final IdempotentConsumerHelper idempotentHelper;

    @KafkaListener(topics = "order.created", groupId = "order-service")
    public void handleOrderCreated(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
                                   @Payload EventEnvelope<Object> envelope,
                                   ConsumerRecord<String, Object> record) {
        // The eventId from the envelope is used for idempotency tracking
        UUID eventId = envelope.eventId();
        String consumerName = "order-service-order-created";
        idempotentHelper.process(consumerName, eventId, () -> {
            // Business logic placeholder – in real code you would update the order saga state etc.
            log.info("Processing order.created event with id {} and key {}", eventId, key);
            // Simulate poisoned message detection – if payload JSON contains the string "poison", throw to trigger retries/DLT
            if (envelope.payload() != null && envelope.payload().toString().contains("poison")) {
                throw new RuntimeException("Poisoned message detected");
            }
            // Example: you could deserialize payload and apply domain actions here.
        });
    }
}
