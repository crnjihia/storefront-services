package com.storefront.orderservice.scheduler;

import com.storefront.orderservice.domain.Order;
import com.storefront.orderservice.domain.SagaState;
import com.storefront.orderservice.messaging.OutboxEvent;
import com.storefront.orderservice.repository.OrderRepository;
import com.storefront.orderservice.repository.SagaStateRepository;
import com.storefront.orderservice.messaging.OutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storefront.common.events.EventEnvelope;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaTimeoutScheduler {

    private final OrderRepository orderRepository;
    private final SagaStateRepository sagaStateRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Check every minute
    @Scheduled(fixedDelayString = "${saga.timeout.check-interval:60000}")
    public void checkForTimeouts() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(10));
        List<SagaState> timedOut = sagaStateRepository.findAll().stream()
                .filter(s -> !"CANCELLED".equals(s.getStep()))
                .filter(s -> s.getUpdatedAt().isBefore(cutoff))
                .toList();
        for (SagaState saga : timedOut) {
            UUID orderId = saga.getOrderId();
            // Update saga
            saga.setStep("CANCELLED");
            saga.setStatus("FAILED");
            saga.setFailureReason("saga timeout");
            saga.setUpdatedAt(Instant.now());
            sagaStateRepository.save(saga);
            // Update order status
            orderRepository.findById(orderId).ifPresent(order -> {
                order.setStatus("CANCELLED");
                orderRepository.save(order);
                // Emit cancellation event via outbox
                try {
                    EventEnvelope<Object> envelope = new EventEnvelope<Object>(UUID.randomUUID(), orderId, Instant.now(), "order.cancelled", "Order cancelled by saga timeout");
                    String payload = objectMapper.writeValueAsString(envelope);
                    OutboxEvent out = OutboxEvent.builder()
                            .aggregateId(orderId)
                            .topic("order.cancelled")
                            .payload(payload)
                            .build();
                    outboxEventRepository.save(out);
                } catch (JsonProcessingException e) {
                    log.error("Failed to emit order.cancelled for timed out order {}", orderId, e);
                }
            });
        }
    }
}
