package com.storefront.notificationservice.controller;

import com.storefront.common.events.DeliveryStatusUpdatedEvent;
import com.storefront.common.events.EventEnvelope;
import com.storefront.notificationservice.domain.DeliveryStatus;
import com.storefront.notificationservice.domain.DeliveryUpdate;
import com.storefront.notificationservice.messaging.IdempotentConsumerHelper;
import com.storefront.notificationservice.messaging.OutboxEvent;
import com.storefront.notificationservice.messaging.OutboxEventRepository;
import com.storefront.notificationservice.repository.DeliveryUpdateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryUpdateRepository deliveryUpdateRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IdempotentConsumerHelper idempotentHelper;

    @PostMapping("/{orderId}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID orderId, @RequestBody DeliveryStatusUpdateRequest request) {
        // Persist delivery update
        DeliveryUpdate update = DeliveryUpdate.builder()
                .orderId(orderId)
                .status(DeliveryStatus.valueOf(request.getStatus()))
                .county(request.getCounty())
                .note(request.getNote())
                .occurredAt(Instant.now())
                .build();
        deliveryUpdateRepository.save(update);

        // Emit delivery.status-updated event via outbox
        UUID eventId = UUID.randomUUID();
        idempotentHelper.process("delivery-status-updater", eventId, () -> {
            try {
                DeliveryStatusUpdatedEvent payload = new DeliveryStatusUpdatedEvent(orderId, request.getStatus(), Instant.now());
                EventEnvelope<Object> envelope = new EventEnvelope<Object>(eventId, orderId, Instant.now(), "delivery.status-updated", payload);
                String json = objectMapper.writeValueAsString(envelope);
                OutboxEvent outbox = OutboxEvent.builder()
                        .aggregateId(orderId)
                        .topic("delivery.status-updated")
                        .payload(json)
                        .build();
                outboxEventRepository.save(outbox);
            } catch (Exception e) {
                // log omitted for brevity
            }
        });
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{orderId}/timeline")
    public ResponseEntity<List<DeliveryUpdate>> getTimeline(@PathVariable UUID orderId) {
        List<DeliveryUpdate> timeline = deliveryUpdateRepository.findByOrderIdOrderByOccurredAtAsc(orderId);
        return ResponseEntity.ok(timeline);
    }

    @Data
    public static class DeliveryStatusUpdateRequest {
        private String status; // should match DeliveryStatus enum values
        private String county;
        private String note;
    }
}
