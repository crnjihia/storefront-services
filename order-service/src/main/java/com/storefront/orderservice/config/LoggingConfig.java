package com.storefront.orderservice.config;

import com.storefront.common.logging.CorrelationIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Cross-cutting configuration for structured logging and distributed tracing.
 * Registers the CorrelationIdFilter at HIGHEST_PRECEDENCE so that all incoming HTTP
 * requests immediately populate SLF4J MDC with correlation keys before any business logic executes.
 */
@Configuration
public class LoggingConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
