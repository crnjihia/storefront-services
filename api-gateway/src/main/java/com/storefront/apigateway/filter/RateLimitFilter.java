package com.storefront.apigateway.filter;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Simple in‑memory rate limiter per IP address.
 * Allows up to {@code MAX_REQUESTS} within a {@code WINDOW} duration.
 */
@Component
@Slf4j
public class RateLimitFilter implements WebFilter, Ordered {

    private static final int MAX_REQUESTS = 100; // per window
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private static class Bucket {
        int count;
        Instant windowStart;
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (ip == null) {
            ip = exchange.getRequest().getRemoteAddress() != null ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
        }
        Bucket bucket = buckets.computeIfAbsent(ip, k -> {
            Bucket b = new Bucket();
            b.windowStart = Instant.now();
            b.count = 0;
            return b;
        });
        synchronized (bucket) {
            Instant now = Instant.now();
            if (now.isAfter(bucket.windowStart.plus(WINDOW))) {
                bucket.windowStart = now;
                bucket.count = 0;
            }
            bucket.count++;
            if (bucket.count > MAX_REQUESTS) {
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // After JWT filter but before routing
        return 0;
    }
}
