package com.storefront.orderservice.domain.model;

public enum OrderStatus {
    PENDING,
    STOCK_RESERVED,
    PAYMENT_INITIATED,
    CONFIRMED,
    CANCELLED
}
