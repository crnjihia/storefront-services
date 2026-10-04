package com.storefront.paymentservice.gateway;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;

@Component
public class CardGateway implements PaymentGateway {

    @Override
    @CircuitBreaker(name = "paymentGatewayCb")
    @Retry(name = "paymentGatewayCb", fallbackMethod = "fallback")
    @TimeLimiter(name = "paymentGatewayCb")
    public CompletableFuture<PaymentResult> process(PaymentRequest request) {
        // Simulate immediate success for card payments
        return CompletableFuture.completedFuture(new PaymentResult(true, "CARD_RECEIPT_" + request.getOrderId(), null));
    }

    public CompletableFuture<PaymentResult> fallback(PaymentRequest request, Throwable throwable) {
        return CompletableFuture.completedFuture(new PaymentResult(false, null, "Card fallback: " + throwable.getMessage()));
    }
}
