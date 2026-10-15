# Release v1.0.0: Storefront Services (Kenyan-Market Jumia Architecture)

## Overview
Initial major release of **Storefront Services**, a production-grade distributed e-commerce backend built with Java 21, Spring Boot 3.3, and Apache Kafka. The platform demonstrates an enterprise Saga checkout engine, transactional outbox publishing, edge routing via Spring Cloud Gateway with Resilience4j circuit breaking, and Kenyan fintech integrations (Safaricom Daraja M-Pesa STK push with HMAC-SHA256 signature verification).

---

## What's Included

### 1. Distributed Services & Shared Event Contracts
- **`common-events` (v0.0.1-SNAPSHOT)**: Shared immutable event schemas (`OrderCreated`, `PaymentCompleted`, `PaymentInitiated`, `NotificationRequested`, `EventEnvelope<T>`) and topic constants. Configured for publishing to **GitHub Packages**.
- **`user-service`**: Customer account management, addresses, and Flyway schema migrations.
- **`catalog-service`**: Atomic inventory reservation and compensation stock-release listener.
- **`order-service`**: Orchestrated Saga coordinator managing the checkout lifecycle (`PENDING` → `STOCK_RESERVED` → `PAYMENT_INITIATED` → `CONFIRMED` / `CANCELLED`).
- **`payment-service`**: M-Pesa STK push simulation, Stripe card processing, idempotency key replay table, and HMAC-SHA256 callback verification.
- **`notification-service`**: Idempotent alert dispatching and Kenyan county distribution tracking.
- **`api-gateway`**: Spring Cloud Gateway edge reverse proxy with Resilience4j circuit breaking (50% threshold, 10s open duration) and JSON fallback with `Retry-After: 10`.

### 2. Architecture & Enterprise Correctness
- **Constructor Injection**: Enforced across all components with zero `@Autowired` on fields or setters.
- **Domain Independence**: Pure Java records (`OrderRecord`, `OrderItemRecord`, `OrderStatus`) free from Spring or JPA dependencies, verified by **ArchUnit** rules.
- **Flyway Only**: `ddl-auto: validate` enforced across all environments.
- **Transactional Outbox**: Atomic database mutations and event generation, polled and published to Kafka.
- **Resilient Kafka Topology**: 18 Kafka topics (9 primary + 9 `.DLT` dead-letter queues) with `DefaultErrorHandler` (3 attempts, 1s interval).
- **Structured JSON Logging & MDC**: `net.logstash.logback:logstash-logback-encoder` with distributed correlation IDs (`traceId`, `spanId`, `X-Request-ID`).

### 3. Verification & Performance
- **Unit & Saga Tests**: 100% pass rate across ArchUnit rules, Saga orchestrator compensation tests, and HMAC signature verifiers.
- **Performance Benchmarks**: Gatling and k6 simulations covering 100, 500, and 1,000 concurrent users with committed latency tables (p99 < 560ms under 1,000 VUs).
- **Interactive Documentation**: OpenAPI 3.0 specification rendered via standalone Redoc on **GitHub Pages** (`docs/index.html`).

---

## Container Images (GitHub Container Registry)
- `ghcr.io/crnjihia/storefront-user-service:latest`
- `ghcr.io/crnjihia/storefront-catalog-service:latest`
- `ghcr.io/crnjihia/storefront-order-service:latest`
- `ghcr.io/crnjihia/storefront-payment-service:latest`
- `ghcr.io/crnjihia/storefront-notification-service:latest`
- `ghcr.io/crnjihia/storefront-api-gateway:latest`

---

## Quick Start
```bash
git clone https://github.com/crnjihia/storefront-services.git
cd storefront-services
git checkout v1.0.0
docker compose up -d --build
```
