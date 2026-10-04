package com.storefront.orderservice.saga;

import com.storefront.common.events.EventEnvelope;
import com.storefront.orderservice.domain.Order;
import com.storefront.orderservice.domain.SagaState;
import com.storefront.orderservice.repository.OrderRepository;
import com.storefront.orderservice.repository.SagaStateRepository;
import com.storefront.orderservice.messaging.OutboxEventRepository;
import com.storefront.orderservice.messaging.IdempotentConsumerHelper;
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
        orchestrator = new OrderSagaOrchestrator(orderRepository, sagaStateRepository, outboxEventRepository, idempotentHelper);
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
        // Simulate idempotent helper invoking the lambda directly
        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(2);
            r.run();
            return null;
        }).when(idempotentHelper).process(anyString(), any(UUID.class), any(Runnable.class));

        EventEnvelope<Object> envelope = new EventEnvelope<>(UUID.randomUUID(), orderId, java.time.Instant.now(), "stock.reserved", "payload");
        orchestrator.handleEvent(envelope.eventId(), "stock.reserved", envelope);

        verify(sagaStateRepository).save(saga);
        verify(orderRepository).save(order);
        verify(outboxEventRepository).save(any());
    }
}
