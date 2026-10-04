package com.storefront.paymentservice.events;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for the payment.refunded event.
 */
public record PaymentRefundedEvent(UUID orderId,
                                 UUID paymentId,
                                 BigDecimal amount,
                                 String reason) {}
