package com.storefront.catalogservice.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storefront.catalogservice.domain.Stock;
import com.storefront.catalogservice.domain.StockReservation;
import com.storefront.catalogservice.domain.StockReservationStatus;
import com.storefront.catalogservice.repository.StockReservationRepository;
import com.storefront.catalogservice.repository.StockRepository;
import com.storefront.catalogservice.messaging.IdempotentConsumerHelper;
import com.storefront.catalogservice.messaging.OutboxEventRepository;
import com.storefront.catalogservice.messaging.OutboxEvent;
import com.storefront.common.events.EventEnvelope;
import com.storefront.common.events.StockReservedEvent;
import com.storefront.common.events.StockReservationFailedEvent;
import com.storefront.common.events.StockReleasedEvent;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogSagaOrchestrator {

    private final StockRepository stockRepository;
    private final StockReservationRepository reservationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotentConsumerHelper idempotentHelper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CONSUMER = "catalog-saga-orchestrator";

    @Transactional
    public void handleOrderCreated(EventEnvelope<Object> envelope) {
        idempotentHelper.process(CONSUMER, envelope.eventId(), () -> processOrderCreated(envelope));
    }

    private void processOrderCreated(EventEnvelope<Object> envelope) {
        UUID orderId = envelope.orderId();
        Object payload = envelope.payload();
        List<Map<String, Object>> items = extractItems(payload);
        if (items == null || items.isEmpty()) {
            log.warn("No order items found in order.created payload for order {}", orderId);
            // Emit reservation-failed with generic reason
            emitOutbox("stock.reservation-failed", orderId,
                    new StockReservationFailedEvent(orderId, null, "No items in order"));
            return;
        }
        // Check availability for all items first
        List<ReservationAttempt> attempts = new ArrayList<>();
        boolean allAvailable = true;
        for (Map<String, Object> item : items) {
            UUID productId = UUID.fromString(item.get("productId").toString());
            int quantity = ((Number) item.get("quantity")).intValue();
            Stock stock = stockRepository.findByProductId(productId).orElse(null);
            if (stock == null) {
                allAvailable = false;
                attempts.add(new ReservationAttempt(productId, quantity, false, "Product not found"));
                continue;
            }
            if (stock.getAvailable() >= quantity) {
                attempts.add(new ReservationAttempt(productId, quantity, true, null));
            } else {
                allAvailable = false;
                attempts.add(new ReservationAttempt(productId, quantity, false, "Insufficient stock"));
            }
        }
        if (!allAvailable) {
            // Emit failure events for each unavailable item
            for (ReservationAttempt attempt : attempts) {
                if (!attempt.success) {
                    emitOutbox("stock.reservation-failed", orderId,
                            new StockReservationFailedEvent(orderId, attempt.productId, attempt.reason));
                }
            }
            return;
        }
        // All items available – perform reservation atomically
        for (ReservationAttempt attempt : attempts) {
            Stock stock = stockRepository.findByProductId(attempt.productId).orElseThrow();
            stock.setAvailable(stock.getAvailable() - attempt.quantity);
            stock.setReserved(stock.getReserved() + attempt.quantity);
            stock.setUpdatedAt(Instant.now());
            stockRepository.save(stock);
            // Create reservation record
            StockReservation reservation = StockReservation.builder()
                    .orderId(orderId)
                    .productId(attempt.productId)
                    .quantity(attempt.quantity)
                    .status(StockReservationStatus.RESERVED)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            reservationRepository.save(reservation);
            // Emit reserved event per product
            emitOutbox("stock.reserved", orderId,
                    new StockReservedEvent(orderId, attempt.productId, attempt.quantity));
        }
    }

    @Transactional
    public void handleRelease(UUID orderId) {
        idempotentHelper.process(CONSUMER, UUID.randomUUID(), () -> processRelease(orderId));
    }

    private void processRelease(UUID orderId) {
        List<StockReservation> reservations = reservationRepository.findByOrderIdAndStatus(orderId, StockReservationStatus.RESERVED);
        for (StockReservation reservation : reservations) {
            // Revert stock
            Stock stock = stockRepository.findByProductId(reservation.getProductId()).orElse(null);
            if (stock != null) {
                stock.setAvailable(stock.getAvailable() + reservation.getQuantity());
                stock.setReserved(stock.getReserved() - reservation.getQuantity());
                stock.setUpdatedAt(Instant.now());
                stockRepository.save(stock);
            }
            // Update reservation status
            reservation.setStatus(StockReservationStatus.RELEASED);
            reservation.setUpdatedAt(Instant.now());
            reservationRepository.save(reservation);
            // Emit released event
            emitOutbox("stock.released", orderId,
                    new StockReleasedEvent(orderId, reservation.getProductId()));
        }
    }

    @Transactional
    public void handleOrderConfirmed(UUID orderId) {
        idempotentHelper.process(CONSUMER, UUID.randomUUID(), () -> processOrderConfirmed(orderId));
    }

    private void processOrderConfirmed(UUID orderId) {
        List<StockReservation> reservations = reservationRepository.findByOrderIdAndStatus(orderId, StockReservationStatus.RESERVED);
        for (StockReservation reservation : reservations) {
            // Decrement reserved count – stock already deducted from available
            Stock stock = stockRepository.findByProductId(reservation.getProductId()).orElse(null);
            if (stock != null) {
                stock.setReserved(stock.getReserved() - reservation.getQuantity());
                stock.setUpdatedAt(Instant.now());
                stockRepository.save(stock);
            }
            reservation.setStatus(StockReservationStatus.CONFIRMED);
            reservation.setUpdatedAt(Instant.now());
            reservationRepository.save(reservation);
        }
    }

    private void emitOutbox(String topic, UUID orderId, Object payload) {
        try {
            EventEnvelope<Object> envelope = new EventEnvelope<>(UUID.randomUUID(), orderId, Instant.now(), topic, payload);
            String json = objectMapper.writeValueAsString(envelope);
            OutboxEvent out = OutboxEvent.builder()
                    .aggregateId(orderId)
                    .topic(topic)
                    .payload(json)
                    .build();
            outboxEventRepository.save(out);
        } catch (JsonProcessingException e) {
            log.error("Failed to emit outbox event {} for order {}", topic, orderId, e);
        }
    }

    /**
     * Helper to extract list of items from the generic payload.
     * Supports payload as Map with key "items" where each item is a Map containing
     * "productId" (String UUID) and "quantity" (Number).
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(Object payload) {
        if (payload instanceof Map map) {
            Object itemsObj = map.get("items");
            if (itemsObj instanceof List) {
                return (List<Map<String, Object>>) itemsObj;
            }
        }
        return null;
    }

    private static class ReservationAttempt {
        final UUID productId;
        final int quantity;
        final boolean success;
        final String reason;
        ReservationAttempt(UUID productId, int quantity, boolean success, String reason) {
            this.productId = productId;
            this.quantity = quantity;
            this.success = success;
            this.reason = reason;
        }
    }
}
