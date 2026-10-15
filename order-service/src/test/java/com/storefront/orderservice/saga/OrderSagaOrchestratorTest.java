package com.storefront.orderservice.saga;

import com.storefront.common.events.EventEnvelope;
import com.storefront.orderservice.domain.Order;
import com.storefront.orderservice.domain.SagaState;
import com.storefront.orderservice.repository.OrderRepository;
import com.storefront.orderservice.repository.SagaStateRepository;
import com.storefront.orderservice.messaging.OutboxEventRepository;
import com.storefront.orderservice.messaging.IdempotentConsumerHelper;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;

class OrderSagaOrchestratorTest {

    private OrderRepository orderRepository;
    private SagaStateRepository sagaStateRepository;
    private OutboxEventRepository outboxEventRepository;
    private IdempotentConsumerHelper idempotentHelper;
    private OrderSagaOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        sagaStateRepository = mock(SagaStateRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        idempotentHelper = mock(IdempotentConsumerHelper.class);
        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(2);
            r.run();
            return null;
        }).when(idempotentHelper).process(any(), any(), any());
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        orchestrator = new OrderSagaOrchestrator(orderRepository, sagaStateRepository, outboxEventRepository, idempotentHelper, objectMapper);
    }

    @Test
    void testHandleStockReservedTransition() {
        UUID orderId = UUID.randomUUID();
        SagaState saga = SagaState.builder()
                .orderId(orderId)
                .step("STARTED")
                .status("IN_PROGRESS")
                .updatedAt(java.time.Instant.now())
                .build();
        when(sagaStateRepository.findById(orderId)).thenReturn(Optional.of(saga));
        Order order = Order.builder().id(orderId).userId(UUID.randomUUID()).status("PENDING").total(java.math.BigDecimal.ZERO).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        EventEnvelope<Object> envelope = new EventEnvelope<>(UUID.randomUUID(), orderId, java.time.Instant.now(), "stock.reserved", "payload");
        orchestrator.handleEvent(envelope.eventId(), "stock.reserved", envelope);

        verify(sagaStateRepository).save(saga);
        verify(orderRepository).save(order);
        verify(outboxEventRepository).save(any());
    }

    @Test
    void testHandlePaymentCompletedHappyPath() {
        UUID orderId = UUID.randomUUID();
        SagaState saga = SagaState.builder()
                .orderId(orderId)
                .step("STOCK_RESERVED")
                .status("IN_PROGRESS")
                .updatedAt(java.time.Instant.now())
                .build();
        when(sagaStateRepository.findById(orderId)).thenReturn(Optional.of(saga));
        Order order = Order.builder().id(orderId).userId(UUID.randomUUID()).status("STOCK_RESERVED").total(BigDecimal.TEN).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        EventEnvelope<Object> envelope = new EventEnvelope<>(UUID.randomUUID(), orderId, java.time.Instant.now(), "payment.completed", "payload");
        orchestrator.handleEvent(envelope.eventId(), "payment.completed", envelope);

        // Verify state is transitioned to PAYMENT_COMPLETED, order is CONFIRMED, and order.confirmed is emitted
        verify(sagaStateRepository).save(argThat(s -> "PAYMENT_COMPLETED".equals(s.getStep())));
        verify(orderRepository).save(argThat(o -> "CONFIRMED".equals(o.getStatus())));
        verify(outboxEventRepository).save(argThat(evt -> "order.confirmed".equals(evt.getTopic())));
    }

    @Test
    void testForcePaymentFailureTriggersCompensationPath() {
        UUID orderId = UUID.randomUUID();
        SagaState saga = SagaState.builder()
                .orderId(orderId)
                .step("STOCK_RESERVED")
                .status("IN_PROGRESS")
                .updatedAt(java.time.Instant.now())
                .build();
        when(sagaStateRepository.findById(orderId)).thenReturn(Optional.of(saga));
        Order order = Order.builder().id(orderId).userId(UUID.randomUUID()).status("STOCK_RESERVED").total(BigDecimal.TEN).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // 1. Force payment failure event
        EventEnvelope<Object> paymentFailedEnv = new EventEnvelope<>(UUID.randomUUID(), orderId, java.time.Instant.now(), "payment.failed", "Insufficient balance on M-Pesa");
        orchestrator.handleEvent(paymentFailedEnv.eventId(), "payment.failed", paymentFailedEnv);

        // Assert compensation: stock.released event emitted and saga marked FAILED/CANCELLED
        verify(outboxEventRepository).save(argThat(evt -> "stock.released".equals(evt.getTopic())));
        verify(sagaStateRepository).save(argThat(s -> "CANCELLED".equals(s.getStep()) && "FAILED".equals(s.getStatus())));

        // 2. Assert that when stock.released compensation completes, order transitions to CANCELLED and emits order.cancelled
        EventEnvelope<Object> stockReleasedEnv = new EventEnvelope<>(UUID.randomUUID(), orderId, java.time.Instant.now(), "stock.released", "Stock successfully restored");
        orchestrator.handleEvent(stockReleasedEnv.eventId(), "stock.released", stockReleasedEnv);

        verify(orderRepository).save(argThat(o -> "CANCELLED".equals(o.getStatus())));
        verify(outboxEventRepository).save(argThat(evt -> "order.cancelled".equals(evt.getTopic())));
    }
}
