package com.storefront.paymentservice.gateway;

import com.storefront.paymentservice.domain.PaymentMethod;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PaymentGateway {
    CompletableFuture<PaymentResult> process(PaymentRequest request);
}

class PaymentRequest {
    private final UUID orderId;
    private final BigDecimal amount;
    private final PaymentMethod method;
    private final String phone; // optional, for MPESA

    public PaymentRequest(UUID orderId, BigDecimal amount, PaymentMethod method, String phone) {
        this.orderId = orderId;
        this.amount = amount;
        this.method = method;
        this.phone = phone;
    }

    public UUID getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentMethod getMethod() { return method; }
    public String getPhone() { return phone; }
}

class PaymentResult {
    private final boolean success;
    private final String receipt; // mpesa receipt if success
    private final String failureReason;

    public PaymentResult(boolean success, String receipt, String failureReason) {
        this.success = success;
        this.receipt = receipt;
        this.failureReason = failureReason;
    }

    public boolean isSuccess() { return success; }
    public String getReceipt() { return receipt; }
    public String getFailureReason() { return failureReason; }
}
