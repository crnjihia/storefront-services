package com.storefront.apigateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final String RETRY_AFTER_SECONDS = "10";

    @RequestMapping(value = "/{service}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> genericFallback(@PathVariable String service) {
        String body = String.format("{\"error\":\"Service unavailable: %s circuit breaker is OPEN. Please retry.\",\"retryAfter\":%s}",
                service, RETRY_AFTER_SECONDS);
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", RETRY_AFTER_SECONDS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));
    }

    @RequestMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> usersFallback() {
        return genericFallback("user-service");
    }

    @RequestMapping(value = "/catalog", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> catalogFallback() {
        return genericFallback("catalog-service");
    }

    @RequestMapping(value = "/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> ordersFallback() {
        return genericFallback("order-service");
    }

    @RequestMapping(value = "/payments", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> paymentsFallback() {
        return genericFallback("payment-service");
    }

    @RequestMapping(value = "/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> notificationsFallback() {
        return genericFallback("notification-service");
    }
}
