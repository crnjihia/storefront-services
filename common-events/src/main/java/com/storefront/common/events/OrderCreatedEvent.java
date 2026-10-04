package com.storefront.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload for the order.created event.
 */
public record OrderCreatedEvent(UUID orderId,
                               UUID customerId,
                               BigDecimal totalAmount,
                               Instant createdAt) {}
