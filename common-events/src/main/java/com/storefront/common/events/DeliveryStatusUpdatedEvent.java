package com.storefront.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload for the delivery.status-updated event.
 */
public record DeliveryStatusUpdatedEvent(UUID orderId,
                                         String status,
                                         Instant updatedAt) {}
