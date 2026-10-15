# ADR 0001: Why Apache Kafka Over RabbitMQ for Storefront Services

## Status
Accepted

## Context
Storefront Services is designed as an event-driven distributed e-commerce architecture modeled on high-concurrency African marketplaces (e.g., Jumia, Copia). In such platforms, events like checkout orders, mobile money (M-Pesa) payments, and stock reservations require durable streaming, replayability, strict partition ordering per aggregate, and distributed saga coordination across independent services.

We evaluated two leading messaging backends:
1. **Apache Kafka** (Distributed Commit Log / Event Streaming Platform)
2. **RabbitMQ** (AMQP-based Message Broker with Exchange/Queue Routing)

## Decision Drivers
* **Event Sourcing & Replayability**: Ability to reprocess events from an arbitrary offset or timestamp after a consumer bugfix, deployment failure, or ledger audit.
* **Partition Ordering by Aggregate ID**: Guarantee that all state changes for a given `orderId` or `userId` are processed strictly in chronological order.
* **High-Throughput / High-Concurrency**: Flash sales (e.g., Black Friday) with sudden bursts of thousands of concurrent checkouts without memory exhaustion.
* **Outbox Pattern Integration**: Seamless polling and publishing of immutable events to distributed topics with consumer offset tracking decoupled from message deletion.

## Options Considered

### Option 1: RabbitMQ
* *Pros*: Advanced flexible routing topologies (topic, direct, headers, fanout), low latency on low-volume message queues, dead-letter routing via queue DLX configuration.
* *Cons*:
  * Messages are deleted upon acknowledgment; historical replay requires building external archival or audit datastores.
  * Message ordering is difficult to maintain across multiple competing consumers on the same queue without single-active-consumer plugins that hurt concurrency.
  * Queue memory footprint grows linearly under backpressure when consumers lag during peak spikes, risking broker memory crashes.

### Option 2: Apache Kafka (Selected)
* *Pros*:
  * **Immutable Append-Only Commit Log**: Events are retained on disk for a configurable retention window (e.g., 7 days), enabling zero-loss replayability, auditing, and asynchronous recovery.
  * **Key-Based Partition Ordering**: Messages partitioned by `orderId` guarantee strict serialized delivery to consumer instances without global locks.
  * **Consumer-Managed Offsets**: High throughput and horizontal scalability with consumer groups; consumers track their own progress without degrading broker performance.
  * **KRaft Mode Simplicity**: Modern Kafka operates in KRaft mode, eliminating ZooKeeper operational overhead and reducing container count.
* *Cons*:
  * Topic creation and partition sizing require upfront capacity planning.
  * Point-to-point routing logic resides in topic naming conventions rather than broker-managed routing exchanges.

## Decision
We chose **Apache Kafka (KRaft mode)** as the system-wide event backbone. All services publish lifecycle events (`order.created`, `payment.completed`, `stock.reserved`, etc.) keyed by `orderId` into Kafka topics with configured `.DLT` dead-letter topics.

## Consequences
* **Positive**:
  * Event replay allows replaying order timelines in staging or recovering from failed downstream consumers without customer impact.
  * Outbox pattern relays write to Kafka with high partition throughput.
  * Microservices consume at their own pace without overwhelming database connection pools.
* **Negative**:
  * Requires robust client-side error handling (`DefaultErrorHandler`, `FixedBackOff`, dead-letter publishing recoverers) to route poisoned messages without stalling the partition.
