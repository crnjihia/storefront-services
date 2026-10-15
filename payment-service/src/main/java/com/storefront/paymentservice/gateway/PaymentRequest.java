package com.storefront.paymentservice.gateway;

import com.storefront.paymentservice.domain.PaymentMethod;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(UUID orderId, BigDecimal amount, PaymentMethod method, String phone) {
    public UUID getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentMethod getMethod() { return method; }
    public String getPhone() { return phone; }
}
