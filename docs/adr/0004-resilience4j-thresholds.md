# 4. Resilience4j Circuit Breaker Thresholds in API Gateway

Date: 2026-10-15

## Status

Accepted

## Context

In an enterprise distributed e-commerce architecture (especially in emerging markets like Kenya with intermittent mobile network fluctuations and third-party payment gateway latency spikes), cascading failures are a primary threat to overall platform availability. 

When a downstream service such as `payment-service` (handling Safaricom Daraja STK Push requests) or `catalog-service` suffers from connection pool exhaustion or high latency, incoming client requests will back up at the edge API gateway (`api-gateway`). Without circuit breaking:
1. Gateway worker threads are tied up waiting for slow/failing downstream responses.
2. Thread pool starvation propagates upstream to mobile and web clients.
3. A single degraded microservice pulls down unaffected services (e.g. browsing the catalog or checking user profiles fails because gateway connections are saturated by stalled payment calls).

We need an edge-level circuit breaking strategy implemented with Resilience4j and Spring Cloud Gateway that fails fast when a service degrades, sheds load to protect downstream resources, and gracefully probes for recovery.

## Decision

We configure Resilience4j CircuitBreakers for each route on Spring Cloud Gateway with the following strict thresholds:

```yaml
resilience4j.circuitbreaker:
  instances:
    catalogServiceCircuitBreaker:
      slidingWindowType: COUNT_BASED
      slidingWindowSize: 10
      minimumNumberOfCalls: 5
      failureRateThreshold: 50.0
      waitDurationInOpenState: 10s
      permittedNumberOfCallsInHalfOpenState: 3
      automaticTransitionFromOpenToHalfOpenEnabled: true
    paymentServiceCircuitBreaker:
      slidingWindowType: COUNT_BASED
      slidingWindowSize: 10
      minimumNumberOfCalls: 5
      failureRateThreshold: 50.0
      waitDurationInOpenState: 10s
      permittedNumberOfCallsInHalfOpenState: 3
      automaticTransitionFromOpenToHalfOpenEnabled: true
```

### Threshold Rationale:

1. **Sliding Window Size (10 calls, minimum 5 calls):**
   - High-volume production services often use a window of 50-100 calls. However, for fast reaction to sudden downtime without requiring dozens of client failures, a 10-call count-based window provides immediate detection within seconds while filtering out isolated transient drops (minimum 5 calls required before evaluating).

2. **Failure Rate Threshold (50%):**
   - If 5 out of 10 consecutive calls fail (HTTP 5xx or connection timeout), the circuit immediately trips OPEN. This distinguishes between benign occasional network blips (< 10%) and systemic service failure (>= 50%).

3. **Wait Duration in Open State (10 seconds):**
   - When OPEN, the gateway immediately short-circuits calls to the fallback endpoint without calling the downstream service. 10 seconds is chosen specifically based on container restart and Kubernetes pod recovery times: it provides sufficient breather time for downstream connection pools and garbage collection pauses to recover, while not locking out clients for an excessive duration.

4. **Half-Open Permitted Calls (3 probe calls):**
   - Once the 10s wait expires, the circuit enters HALF_OPEN. It permits exactly 3 probe requests to hit downstream. If any probe fails, it trips back to OPEN for another 10 seconds. If all succeed, the circuit resets to CLOSED.

5. **JSON Fallback with `Retry-After: 10` Header:**
   - Rather than dropping connections or returning generic HTML error pages, the gateway fallback controller returns a structured RFC 7807 problem JSON:
     ```json
     {
       "timestamp": "2026-10-15T00:00:00Z",
       "status": 503,
       "error": "Service Unavailable",
       "message": "Downstream service is temporarily unavailable. Circuit breaker OPEN.",
       "retryAfterSeconds": 10
     }
     ```
   - Accompanied by the standard HTTP header: `Retry-After: 10`. This allows client SDKs (iOS/Android Jumia clients) to display user-friendly exponential backoff indicators rather than repeated frantic user tap retries.

## Consequences

### Positive:
- **Blast Radius Containment:** Degraded services do not exhaust API gateway threads.
- **Client Predictability:** Clients receive deterministic 503 Service Unavailable responses with exact retry windows.
- **Self-Healing:** Smooth recovery without manual intervention via automated HALF_OPEN probing.

### Negative:
- **False Positives on Low Traffic:** Under very low traffic conditions (e.g. deep night), 3 errors out of 6 calls trips the circuit. This is mitigated by `minimumNumberOfCalls: 5`.
- **Cached vs Live Fallbacks:** Some read routes can return stale Redis caches; however, for financial write routes (`payment-service`), fallbacks must fail fast rather than return synthetic data.
