package com.storefront.paymentservice.gateway;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MpesaStkPushGateway implements PaymentGateway {

    @Value("${payment.mpesa.success-rate:0.9}")
    private double successRate;

    @Value("${payment.mpesa.delay-ms:2000}")
    private long delayMs;

    private final Random random = new Random();

    @Override
    @CircuitBreaker(name = "paymentGatewayCb")
    @Retry(name = "paymentGatewayCb", fallbackMethod = "fallback")
    @TimeLimiter(name = "paymentGatewayCb")
    public CompletableFuture<PaymentResult> process(PaymentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            boolean success = random.nextDouble() < successRate;
            if (success) {
                String receipt = "MPESA_RECEIPT_" + request.getOrderId();
                return new PaymentResult(true, receipt, null);
            } else {
                return new PaymentResult(false, null, "MPESA payment failed");
            }
        });
    }

    // Fallback used by @Retry when all attempts fail
    public CompletableFuture<PaymentResult> fallback(PaymentRequest request, Throwable throwable) {
        return CompletableFuture.completedFuture(new PaymentResult(false, null, "MPESA fallback: " + throwable.getMessage()));
    }
}
