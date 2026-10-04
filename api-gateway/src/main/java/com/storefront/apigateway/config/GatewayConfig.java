package com.storefront.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
            // Users and Auth
            .route("users", r -> r.path("/api/users/**", "/api/auth/**")
                .filters(f -> f.stripPrefix(1)
                                 .circuitBreaker(config -> config.setName("usersCB").setFallbackUri("forward:/fallback/users")))
                .uri("http://user-service:8081"))
            // Catalog (public GET for products)
            .route("catalog", r -> r.path("/api/products/**")
                .filters(f -> f.stripPrefix(1)
                                 .circuitBreaker(config -> config.setName("catalogCB").setFallbackUri("forward:/fallback/catalog")))
                .uri("http://catalog-service:8082"))
            // Orders
            .route("orders", r -> r.path("/api/orders/**")
                .filters(f -> f.stripPrefix(1)
                                 .circuitBreaker(config -> config.setName("ordersCB").setFallbackUri("forward:/fallback/orders")))
                .uri("http://order-service:8083"))
            // Payments
            .route("payments", r -> r.path("/api/payments/**")
                .filters(f -> f.stripPrefix(1)
                                 .circuitBreaker(config -> config.setName("paymentsCB").setFallbackUri("forward:/fallback/payments")))
                .uri("http://payment-service:8084"))
            // Notifications and Deliveries
            .route("notifications", r -> r.path("/api/notifications/**", "/api/deliveries/**")
                .filters(f -> f.stripPrefix(1)
                                 .circuitBreaker(config -> config.setName("notificationsCB").setFallbackUri("forward:/fallback/notifications")))
                .uri("http://notification-service:8085"))
            .build();
    }
}
