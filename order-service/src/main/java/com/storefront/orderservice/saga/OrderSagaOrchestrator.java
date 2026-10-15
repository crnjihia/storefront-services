package com.storefront.orderservice.saga;

import com.storefront.common.events.EventEnvelope;
import com.storefront.orderservice.domain.Order;
import com.storefront.orderservice.domain.SagaState;
import com.storefront.orderservice.messaging.IdempotentConsumerHelper;
import com.storefront.orderservice.messaging.OutboxEvent;
import com.storefront.orderservice.messaging.OutboxEventRepository;
import com.storefront.orderservice.repository.OrderRepository;
import com.storefront.orderservice.repository.SagaStateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {

    private final OrderRepository orderRepository;
    private final SagaStateRepository sagaStateRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotentConsumerHelper idempotentHelper;
    private final ObjectMapper objectMapper;

    private static final String CONSUMER = "order-saga-orchestrator";

    @Transactional
    public void handleEvent(UUID eventId, String topic, EventEnvelope<Object> envelope) {
        // Ensure idempotent processing per consumer
        idempotentHelper.process(CONSUMER, eventId, () -> process(topic, envelope));
    }

    private void process(String topic, EventEnvelope<Object> envelope) {
        UUID orderId = envelope.orderId();
        Optional<SagaState> optSaga = sagaStateRepository.findById(orderId);
        if (optSaga.isEmpty()) {
            log.warn("Saga state not found for order {}", orderId);
            return;
        }
        SagaState saga = optSaga.get();

        switch (topic) {
            case "stock.reserved" -> handleStockReserved(saga);
            case "stock.reservation-failed" -> handleStockReservationFailed(saga);
            case "payment.completed" -> handlePaymentCompleted(saga);
            case "payment.failed" -> handlePaymentFailed(saga);
            case "stock.released" -> handleStockReleased(saga);
            default -> log.warn("Unhandled saga event topic: {}", topic);
        }
    }

    private void handleStockReserved(SagaState saga) {
        if (!"STARTED".equals(saga.getStep())) {
            return; // ignore if not expected step
        }
        saga.setStep("STOCK_RESERVED");
        saga.setStatus("IN_PROGRESS");
        saga.setUpdatedAt(Instant.now());
        sagaStateRepository.save(saga);
        // Transition order status
        orderRepository.findById(saga.getOrderId()).ifPresent(order -> {
            order.setStatus("STOCK_RESERVED");
            orderRepository.save(order);
            // Emit next step event – request payment (could be a command to payment service)
            emitOutbox("payment.request", order.getId(), "Requesting payment");
        });
    }

    private void handleStockReservationFailed(SagaState saga) {
        saga.setStep("CANCELLED");
        saga.setStatus("FAILED");
        saga.setFailureReason("stock reservation failed");
        saga.setUpdatedAt(Instant.now());
        sagaStateRepository.save(saga);
        // Update order status
        orderRepository.findById(saga.getOrderId()).ifPresent(order -> {
            order.setStatus("CANCELLED");
            orderRepository.save(order);
            emitOutbox("order.cancelled", order.getId(), "Order cancelled due to stock failure");
        });
    }

    private void handlePaymentCompleted(SagaState saga) {
        if (!"STOCK_RESERVED".equals(saga.getStep())) {
            return;
        }
        saga.setStep("PAYMENT_COMPLETED");
        saga.setStatus("IN_PROGRESS");
        saga.setUpdatedAt(Instant.now());
        sagaStateRepository.save(saga);
        // Update order status and emit confirmed
        orderRepository.findById(saga.getOrderId()).ifPresent(order -> {
            order.setStatus("CONFIRMED");
            orderRepository.save(order);
            // Final confirmation event
            emitOutbox("order.confirmed", order.getId(), "Order confirmed");
        });
    }

    private void handlePaymentFailed(SagaState saga) {
        // Emit compensation: release stock then cancel order
        emitOutbox("stock.released", saga.getOrderId(), "Releasing stock due to payment failure");
        // Mark saga as cancelled (will be finalized when stock.released is processed)
        saga.setStep("CANCELLED");
        saga.setStatus("FAILED");
        saga.setFailureReason("payment failed");
        saga.setUpdatedAt(Instant.now());
        sagaStateRepository.save(saga);
    }

    private void handleStockReleased(SagaState saga) {
        // After compensation stock release, finalize cancellation
        orderRepository.findById(saga.getOrderId()).ifPresent(order -> {
            order.setStatus("CANCELLED");
            orderRepository.save(order);
            emitOutbox("order.cancelled", order.getId(), "Order cancelled after stock release");
        });
    }

    private void emitOutbox(String topic, UUID orderId, String message) {
        try {
            EventEnvelope<Object> envelope = new EventEnvelope<Object>(UUID.randomUUID(), orderId, Instant.now(), topic, message);
            String payload = objectMapper.writeValueAsString(envelope);
            OutboxEvent out = OutboxEvent.builder()
                    .aggregateId(orderId)
                    .topic(topic)
                    .payload(payload)
                    .build();
            outboxEventRepository.save(out);
        } catch (JsonProcessingException e) {
            log.error("Failed to emit outbox event {} for order {}", topic, orderId, e);
        }
    }
}
