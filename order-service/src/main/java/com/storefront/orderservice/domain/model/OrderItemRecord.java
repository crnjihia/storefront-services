package com.storefront.orderservice.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemRecord(
    UUID id,
    UUID productId,
    int quantity,
    BigDecimal unitPrice
) {
    public OrderItemRecord {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Unit price cannot be negative");
        }
    }
}
