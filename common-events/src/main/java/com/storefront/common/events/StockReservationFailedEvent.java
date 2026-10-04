package com.storefront.common.events;

import java.util.UUID;

/**
 * Payload for the stock.reservation-failed event.
 */
public record StockReservationFailedEvent(UUID orderId,
                                           UUID productId,
                                           String reason) {}
