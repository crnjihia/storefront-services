package com.storefront.common.events;

import java.util.UUID;

/**
 * Payload for the payment.failed event.
 */
public record PaymentFailedEvent(UUID orderId,
                                 UUID paymentId,
                                 String reason) {}
