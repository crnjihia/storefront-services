package com.storefront.orderservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pure domain representation of an Order aggregate.
 * Does not depend on Spring or JPA framework annotations.
 */
public record OrderRecord(
    UUID id,
    UUID userId,
    OrderStatus status,
    BigDecimal total,
    Instant createdAt,
    List<OrderItemRecord> items
) {
    public OrderRecord {
        if (id == null) {
            throw new IllegalArgumentException("Order id must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User id must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Order status must not be null");
        }
        if (total == null || total.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total amount must be non-negative");
        }
        items = (items == null) ? List.of() : List.copyOf(items);
    }

    public boolean canBeCancelled() {
        return status != OrderStatus.CONFIRMED && status != OrderStatus.CANCELLED;
    }
}
