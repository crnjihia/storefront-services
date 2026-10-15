# Kafka Event Topology, Retries, and Dead-Letter Queue (DLT) Architecture

## 1. Overview
In `storefront-services`, asynchronous communication between services is coordinated over Apache Kafka. To guarantee message durability, exactly-once processing semantics at the application level, and operational resilience against downstream failures, every topic enforces a standardized retry policy and routes unrecoverable messages to a dedicated Dead-Letter Topic (`*.DLT`).

---

## 2. Topic Inventory & Specifications

All primary topics are provisioned with 3 partitions (keyed by `orderId` or `userId` to ensure strict in-order processing per entity) and a retention policy of 7 days in production.

| Primary Topic | Key | Payload DTO | Dead-Letter Topic (*.DLT) | Publishing Service | Primary Consumer(s) |
|---|---|---|---|---|---|
| `order.created` | `orderId` (UUID) | `OrderCreatedEvent` | `order.created.DLT` | `order-service` | `catalog-service` |
| `order.cancelled` | `orderId` (UUID) | `OrderCancelledEvent` | `order.cancelled.DLT` | `order-service` | `catalog-service`, `notification-service` |
| `order.confirmed` | `orderId` (UUID) | `OrderConfirmedEvent` | `order.confirmed.DLT` | `order-service` | `notification-service` |
| `stock.reserved` | `orderId` (UUID) | `StockReservedEvent` | `stock.reserved.DLT` | `catalog-service` | `order-service` |
| `stock.released` | `orderId` (UUID) | `StockReleasedEvent` | `stock.released.DLT` | `order-service` | `catalog-service` |
| `payment.initiated` | `orderId` (UUID) | `PaymentInitiated` | `payment.initiated.DLT` | `order-service` | `payment-service` |
| `payment.completed` | `orderId` (UUID) | `PaymentCompleted` | `payment.completed.DLT` | `payment-service` | `order-service` |
| `payment.failed` | `orderId` (UUID) | `PaymentFailedEvent` | `payment.failed.DLT` | `payment-service` | `order-service` |
| `notification.send` | `recipientId` / `orderId` | `NotificationRequested` | `notification.send.DLT` | `order-service`, `payment-service` | `notification-service` |

---

## 3. Retry and DLT Error Handling Topology

### Standard Consumer Error Handling
Every Spring Kafka consumer container factory across all microservices is configured with Spring Kafka's `DefaultErrorHandler`:

```java
@Bean
public DefaultErrorHandler defaultErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
    // 1. Recoverer routes failed messages to "<originalTopic>.DLT" preserving partition and headers
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
        (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

    // 2. FixedBackOff: 1000ms delay, 2 retry attempts (total 3 attempts: 1 original + 2 retries)
    FixedBackOff backOff = new FixedBackOff(1000L, 2L);

    DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
    
    // 3. Mark non-retryable exceptions to immediately route to DLT
    errorHandler.addNotRetryableExceptions(
        IllegalArgumentException.class,
        org.springframework.messaging.converter.MessageConversionException.class,
        com.fasterxml.jackson.core.JsonParseException.class
    );
    
    return errorHandler;
}
```

### Lifecycle of a Message Under Error:
```
[Kafka Topic: payment.initiated]
            │
            ▼
┌───────────────────────────────┐
│ Attempt 1 (Immediate)         │ ───► Fails (e.g., Safaricom API timeout)
└───────────────────────────────┘
            │
            ▼ (Wait 1000ms FixedBackOff)
┌───────────────────────────────┐
│ Attempt 2 (Retry 1)           │ ───► Fails
└───────────────────────────────┘
            │
            ▼ (Wait 1000ms FixedBackOff)
┌───────────────────────────────┐
│ Attempt 3 (Retry 2)           │ ───► Exhausted!
└───────────────────────────────┘
            │
            ▼
┌────────────────────────────────────────────────────────┐
│ DeadLetterPublishingRecoverer                          │
│ Emits message with original payload & error headers to:│
│ Topic: payment.initiated.DLT                           │
└────────────────────────────────────────────────────────┘
```

### DLT Record Metadata Headers
When a message is diverted to `*.DLT`, the recoverer automatically attaches standard Kafka diagnostic headers:
- `kafka_dlt-original-topic`: Name of the source topic (e.g. `order.created`).
- `kafka_dlt-original-partition`: The partition where the failure occurred.
- `kafka_dlt-original-offset`: Source offset.
- `kafka_dlt-exception-fqcn`: Fully-qualified class name of the root exception.
- `kafka_dlt-exception-message`: Detailed exception message.
- `kafka_dlt-exception-stacktrace`: Stack trace string.

---

## 4. Operational Monitoring & Replay of DLTs

1. **Alerting:** An alert triggers when messages are detected on any `*.DLT` topic via Prometheus Kafka Exporter (`kafka_consumergroup_lag{topic=~".*\\.DLT"} > 0`).
2. **Dead-Letter Storage & Replay:** In addition to Kafka DLT topics, services maintain a `dlt_events` audit table in Postgres to record poisoned messages. Ops engineers can trigger replay via an administrative endpoint after fixing underlying bugs or third-party outages:
   `POST /actuator/kafka-dlt/replay?topic=payment.initiated.DLT`
