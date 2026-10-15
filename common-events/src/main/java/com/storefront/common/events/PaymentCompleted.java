package com.storefront.common.events;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCompleted(UUID orderId,
                               UUID paymentId,
                               BigDecimal amount,
                               String method) {}
