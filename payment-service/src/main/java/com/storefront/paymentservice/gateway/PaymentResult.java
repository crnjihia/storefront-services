package com.storefront.paymentservice.gateway;

public record PaymentResult(boolean success, String receipt, String failureReason) {
    public boolean isSuccess() { return success; }
    public String getReceipt() { return receipt; }
    public String getFailureReason() { return failureReason; }
}
