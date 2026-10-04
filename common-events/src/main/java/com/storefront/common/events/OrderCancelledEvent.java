package com.storefront.common.events;

import java.util.UUID;

/**
 * Payload for the order.cancelled event.
 */
public record OrderCancelledEvent(UUID orderId,
                                 String cancellationReason) {}
