package com.storefront.notificationservice.listener;

import com.storefront.common.events.EventEnvelope;
import com.storefront.common.events.Topics;
import com.storefront.notificationservice.domain.NotificationChannel;
import com.storefront.notificationservice.service.NotificationSender;
import com.storefront.notificationservice.messaging.IdempotentConsumerHelper;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final IdempotentConsumerHelper idempotentHelper;
    private final NotificationSender notificationSender;

    // Listen to relevant saga events
    @KafkaListener(topics = {Topics.ORDER_CONFIRMED, Topics.ORDER_CANCELLED, Topics.PAYMENT_FAILED}, groupId = "notification-service")
    public void handle(ConsumerRecord<String, Object> record) {
        @SuppressWarnings("unchecked")
        EventEnvelope<Object> envelope = (EventEnvelope<Object>) record.value();
        if (envelope == null) {
            return;
        }
        UUID eventId = envelope.eventId();
        String consumerName = "notification-service-event-listener";
        idempotentHelper.process(consumerName, eventId, () -> processEvent(envelope));
    }

    private void processEvent(EventEnvelope<Object> envelope) {
        String topic = envelope.type(); // The type field holds the topic name
        UUID orderId = envelope.orderId();
        // Determine notification details based on topic
        String message;
        NotificationChannel channel = NotificationChannel.SMS; // default to SMS simulation
        String recipient = "customer"; // placeholder; in real system would map orderId to user contact
        switch (topic) {
            case Topics.ORDER_CONFIRMED -> message = "Your order " + orderId + " has been confirmed.";
            case Topics.ORDER_CANCELLED -> message = "Your order " + orderId + " has been cancelled.";
            case Topics.PAYMENT_FAILED -> message = "Payment for order " + orderId + " failed. Please try again.";
            default -> message = "Notification for order " + orderId + " (topic: " + topic + ")";
        }
        // Send simulated notification (userId unknown, pass null)
        notificationSender.send(null, channel, recipient, message);
    }
}
