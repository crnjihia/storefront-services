package com.storefront.common.events;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for the payment.completed event.
 */
public record PaymentCompletedEvent(UUID orderId,
                                   UUID paymentId,
                                   BigDecimal amount,
                                   String method) {}
