package com.storefront.paymentservice.gateway;

import java.util.concurrent.CompletableFuture;

public interface PaymentGateway {
    CompletableFuture<PaymentResult> process(PaymentRequest request);
}
