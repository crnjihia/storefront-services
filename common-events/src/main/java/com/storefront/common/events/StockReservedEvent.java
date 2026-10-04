package com.storefront.common.events;

import java.util.UUID;

/**
 * Payload for the stock.reserved event.
 */
public record StockReservedEvent(UUID orderId,
                                 UUID productId,
                                 int quantity) {}
