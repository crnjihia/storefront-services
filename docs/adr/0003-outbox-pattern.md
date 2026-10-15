# ADR 0003: Transactional Outbox Pattern for Reliable Event Publishing

## Status
Accepted

## Context
In microservices architectures, updating a database and publishing a corresponding event to a message broker (Kafka) is a fundamental dual-write operation. A naive implementation follows the "Write-Then-Publish" or "Publish-Then-Write" approach.

### Why "Write-Then-Publish" is Broken
Consider the standard pattern:
```java
@Transactional
public void createOrder(CreateOrderRequest request) {
    Order order = orderRepository.save(new Order(...)); // Step 1: Commit to DB
    kafkaTemplate.send("order.created", order.getId(), event); // Step 2: Publish to Kafka
}
```

This pattern breaks in multiple real-world scenarios:
1. **Network Partition / Broker Outage After DB Commit**:
   * If Kafka is unreachable or times out during Step 2, the application throws an exception.
   * If the database transaction has already committed (or commits anyway), the business change exists in the database, but downstream services (Catalog, Payment) NEVER receive the event. The checkout stalls indefinitely.
2. **App Crash Between Step 1 and Step 2**:
   * If the JVM crashes, the container is killed (OOM), or the node restarts after Step 1, the event is permanently lost.
3. **Transaction Rollback After Publish**:
   * If the message is published asynchronously before the database transaction successfully commits (or if a DB constraint fails upon commit), downstream services process phantom events for state that never existed in the database!

Dual-writes across two heterogeneous systems (PostgreSQL and Kafka) without distributed 2PC cannot guarantee atomicity.

## Decision Drivers
* **At-Least-Once Delivery Guarantee**: Zero event loss, even during database reboots, Kafka broker failovers, or container crashes.
* **ACID Transaction Boundary**: Business entity mutations and event creation must succeed or fail together atomically in the same local relational transaction.
* **Decoupled Publishing Performance**: Database writes must not block on Kafka acknowledgment latency.

## Decision
We implement the **Transactional Outbox Pattern** across all emitting services (`order-service`, `catalog-service`, `payment-service`, `notification-service`).

### Architecture & Data Flow
1. **Atomic Local Write**:
   Within a single `@Transactional` method, the service persists both the domain entity and an `OutboxEvent` row into an `outbox_events` table in the local service database.
   ```sql
   INSERT INTO orders (id, user_id, status, total) VALUES (...);
   INSERT INTO outbox_events (id, aggregate_id, topic, payload, created_at) VALUES (...);
   COMMIT;
   ```
2. **Asynchronous Polling & Dispatch**:
   An `OutboxPublisher` background worker periodically polls for unpublished rows (`WHERE published_at IS NULL ORDER BY created_at ASC`), dispatches them to Kafka using `KafkaTemplate`, and sets `published_at = CURRENT_TIMESTAMP` upon successful broker acknowledgment (`RecordMetadata`).
3. **Idempotent Consumers**:
   Because the outbox publisher guarantees *at-least-once* delivery, transient network timeouts during acknowledgment may cause redelivery. Every consumer microservice records processed event IDs in a `processed_events` table within its local database, ensuring strict deduplication.

## Consequences
* **Positive**:
  * Complete protection against data inconsistency and phantom events.
  * Local DB transactions complete in single-digit milliseconds without blocking on network round-trips to Kafka.
  * Outbox rows provide an auditable event ledger for compliance and forensic debugging.
* **Negative**:
  * Slight latency (100ms–1s poll interval) between DB commit and Kafka arrival.
  * Relational table growth requiring periodic purging of published rows.
