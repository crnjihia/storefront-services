package com.storefront.common.events;

/**
 * Constants for Kafka topic names used across the system.
 */
public final class Topics {
    private Topics() {}

    public static final String ORDER_CREATED = "order.created";
    public static final String STOCK_RESERVED = "stock.reserved";
    public static final String STOCK_RESERVATION_FAILED = "stock.reservation-failed";
    public static final String PAYMENT_INITIATED = "payment.initiated";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String STOCK_RELEASED = "stock.released";
    public static final String ORDER_CONFIRMED = "order.confirmed";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String NOTIFICATION_SEND = "notification.send";
    public static final String DELIVERY_STATUS_UPDATED = "delivery.status-updated";

    /**
     * Helper to create a retry topic name.
     */
    public static String retryTopic(String base, int attempt) {
        return base + ".retry-" + attempt;
    }

    /**
     * Helper to create a dead-letter topic name.
     */
    public static String deadLetterTopic(String base) {
        return base + ".DLT";
    }
}
