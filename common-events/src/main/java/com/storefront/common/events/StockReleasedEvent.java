package com.storefront.common.events;

import java.util.UUID;

/**
 * Payload for the stock.released event.
 */
public record StockReleasedEvent(UUID orderId,
                                 UUID productId) {}
