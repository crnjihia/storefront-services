package com.storefront.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload for the order.confirmed event.
 */
public record OrderConfirmedEvent(UUID orderId,
                                 Instant confirmedAt) {}
