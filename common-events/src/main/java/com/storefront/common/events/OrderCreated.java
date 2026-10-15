package com.storefront.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderCreated(UUID orderId,
                           UUID customerId,
                           BigDecimal totalAmount,
                           Instant createdAt) {}
