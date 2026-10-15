package com.storefront.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Global JWT authentication filter for the API Gateway.
 * Validates JWT using a shared secret and adds X-User-Id and X-User-Role headers for downstream services.
 * Excludes public paths: /api/auth/**, GET /api/products/**, /api/payments/mpesa/callback.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter implements WebFilter, Ordered {

    // Secret injected from configuration (e.g., application.yml)
    @org.springframework.beans.factory.annotation.Value("${jwt.secret}")
    private String secret;
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth",
            "/api/payments/mpesa/callback"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        // Public path checks
        if (isPublic(request)) {
            return chain.filter(exchange);
        }
        List<String> authHeaders = request.getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION);
        if (authHeaders.isEmpty() || !authHeaders.get(0).startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        String token = authHeaders.get(0).substring(7);
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            String userId = claims.getSubject();
            String role = claims.get("role", String.class);
            // Mutate request to add headers
            ServerHttpRequest mutated = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Role", role)
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return unauthorized(exchange);
        }
    }

    private boolean isPublic(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        // match prefix for auth
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        // GET products is public
        if ("GET".equalsIgnoreCase(request.getMethod().name()) && path.startsWith("/api/products/")) {
            return true;
        }
        // specific callback path is public
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        // Execute early, before routing
        return -1;
    }
}
