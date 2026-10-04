package com.storefront.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storefront.common.events.EventEnvelope;
import com.storefront.orderservice.dto.CreateOrderRequest;
import com.storefront.orderservice.dto.OrderResponse;
import com.storefront.orderservice.domain.Order;
import com.storefront.orderservice.domain.OrderItem;
import com.storefront.orderservice.domain.SagaState;
import com.storefront.orderservice.messaging.OutboxEvent;
import com.storefront.orderservice.messaging.OutboxEventRepository;
import com.storefront.orderservice.repository.OrderItemRepository;
import com.storefront.orderservice.repository.OrderRepository;
import com.storefront.orderservice.repository.SagaStateRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SagaStateRepository sagaStateRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public UUID createOrder(CreateOrderRequest request) {
        // Compute total
        BigDecimal total = request.getItems().stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .userId(request.getUserId())
                .status("PENDING")
                .total(total)
                .createdAt(Instant.now())
                .build();
        orderRepository.save(order);

        List<OrderItem> savedItems = new ArrayList<>();
        for (CreateOrderRequest.OrderItemDto dto : request.getItems()) {
            OrderItem item = OrderItem.builder()
                    .id(UUID.randomUUID())
                    .orderId(order.getId())
                    .productId(dto.getProductId())
                    .quantity(dto.getQuantity())
                    .price(dto.getPrice())
                    .createdAt(Instant.now())
                    .build();
            savedItems.add(orderItemRepository.save(item));
        }

        // Initialise saga state
        SagaState saga = SagaState.builder()
                .orderId(order.getId())
                .step("STARTED")
                .status("IN_PROGRESS")
                .updatedAt(Instant.now())
                .build();
        sagaStateRepository.save(saga);

        // Emit order.created via outbox
        try {
            String payload = objectMapper.writeValueAsString(
                    new EventEnvelope<Object>(order.getId(), order.getId(), Instant.now(), "order.created", order));
            OutboxEvent outbox = OutboxEvent.builder()
                    .aggregateId(order.getId())
                    .topic("order.created")
                    .payload(payload)
                    .build();
            outboxEventRepository.save(outbox);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize order.created event for order {}", order.getId(), e);
        }
        return order.getId();
    }

    public Optional<OrderResponse> getOrder(UUID id) {
        return orderRepository.findById(id).map(this::toResponse);
    }

    public List<OrderResponse> getOrdersByUser(UUID userId) {
        return orderRepository.findAll().stream()
                .filter(o -> o.getUserId().equals(userId))
                .map(this::toResponse)
                .toList();
    }

    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream().map(this::toResponse).toList();
    }

    private OrderResponse toResponse(Order order) {
        List<OrderResponse.OrderItemDto> items = orderItemRepository.findAll().stream()
                .filter(i -> i.getOrderId().equals(order.getId()))
                .map(i -> OrderResponse.OrderItemDto.builder()
                        .id(i.getId())
                        .productId(i.getProductId())
                        .quantity(i.getQuantity())
                        .price(i.getPrice())
                        .build())
                .toList();
        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus())
                .total(order.getTotal())
                .createdAt(order.getCreatedAt())
                .items(items)
                .build();
    }
}
