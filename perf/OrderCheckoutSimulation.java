package com.storefront.perf;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling 3.10+ Java Simulation: Order Checkout Throughput & Latency Benchmark.
 * Simulates concurrent Kenyan mobile shoppers placing orders through Spring Cloud Gateway.
 * Evaluates performance under 100, 500, and 1,000 concurrent virtual users.
 */
public class OrderCheckoutSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(System.getProperty("baseUrl", "http://localhost:8080"))
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    private final ScenarioBuilder checkoutScenario = scenario("Order Checkout Scenario")
            .exec(session -> {
                String idempotencyKey = UUID.randomUUID().toString();
                return session.set("idempotencyKey", idempotencyKey);
            })
            .exec(http("POST /api/v1/orders")
                    .post("/api/v1/orders")
                    .header("Idempotency-Key", "#{idempotencyKey}")
                    .header("X-Request-ID", "#{idempotencyKey}")
                    .body(StringBody("""
                        {
                          "userId": "11111111-1111-1111-1111-111111111111",
                          "customerPhone": "254712345678",
                          "items": [
                            {
                              "productId": "33333333-3333-3333-3333-333333333331",
                              "quantity": 1,
                              "unitPrice": 44999.00
                            }
                          ]
                        }
                    """)).asJson()
                    .check(status().in(200, 202)))
            .pause(Duration.ofMillis(500), Duration.ofMillis(1200));

    {
        setUp(
                // Stage 1: 100 Concurrent Users (Baseline Load)
                checkoutScenario.injectOpen(
                        rampUsers(100).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(100.0).during(Duration.ofMinutes(1))
                ),
                // Stage 2: 500 Concurrent Users (Normal Peak Traffic)
                checkoutScenario.injectOpen(
                        nothingFor(Duration.ofSeconds(10)),
                        rampUsers(500).during(Duration.ofSeconds(45)),
                        constantUsersPerSec(500.0).during(Duration.ofMinutes(2))
                ),
                // Stage 3: 1,000 Concurrent Users (Black Friday / Flash Sale Surge)
                checkoutScenario.injectOpen(
                        nothingFor(Duration.ofSeconds(15)),
                        rampUsers(1000).during(Duration.ofSeconds(60)),
                        constantUsersPerSec(1000.0).during(Duration.ofMinutes(2))
                )
        ).protocols(httpProtocol)
         .assertions(
                 global().successfulRequests().percent().gt(99.0),
                 details("POST /api/v1/orders").responseTime().percentile3().lt(500) // p99 < 500ms
         );
    }
}
