package com.storefront.common.events;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentInitiated(UUID orderId,
                              BigDecimal amount,
                              String method,
                              String phone) {}
