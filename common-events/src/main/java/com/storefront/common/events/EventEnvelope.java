package com.storefront.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic envelope that wraps any event payload.
 *
 * @param <T> the type of the payload
 */
public record EventEnvelope<T>(UUID eventId,
                               UUID orderId,
                               Instant occurredAt,
                               String type,
                               T payload) {}
