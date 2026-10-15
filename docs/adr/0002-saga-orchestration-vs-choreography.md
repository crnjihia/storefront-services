# ADR 0002: Distributed Saga Coordination — Orchestration vs Choreography

## Status
Accepted

## Context
In Storefront Services, a customer checkout initiates a distributed business transaction spanning three independent microservices:
1. **Catalog Service**: Atomic stock reservation and inventory hold.
2. **Payment Service**: Mobile money STK push (M-Pesa) or card debit.
3. **Notification Service**: Delivery tracker initialization and dispatch alerts.

Because distributed transactions spanning multiple databases cannot rely on 2-Phase Commit (2PC) without introducing severe lock contention, reduced availability, and coordinator single-points-of-failure, we must use the **Saga Pattern**.

We evaluated two architectural styles for Saga execution:
1. **Choreography**: Each service listens to domain events and autonomously decides what action to execute next.
2. **Orchestration**: A dedicated coordinator (`OrderSagaOrchestrator` in `order-service`) explicitly models saga state, invokes saga participants, and initiates compensating transactions on failure.

## Decision Drivers
* **Centralized Visibility & Observability**: Ability to inspect the exact progress, state, and failure cause of any checkout from a single query.
* **Complex Compensation Logic**: Rollback workflows (releasing stock, triggering customer refunds, sending failure SMS) must be deterministic.
* **Cyclic Dependency Elimination**: Prevent spaghetti event webs where services mutually listen to each other's domain events.
* **Auditability & SLAs**: Kenyan e-commerce operations require tracking drop-offs, payment timeouts (e.g., STK push expirations), and automated compensations.

## Options Considered

### Option 1: Choreographed Saga
* *Pros*: Simple for trivial 2-step transactions; no centralized coordinator service required.
* *Cons*:
  * Difficult to track end-to-end checkout status; business state is distributed across multiple databases.
  * Risk of circular dependencies as participants emit and listen to each other's events.
  * Testing rollback paths requires orchestrating integration assertions across all participant logs.

### Option 2: Orchestrated Saga via `order-service` (Selected)
* *Pros*:
  * Single source of truth: `saga_state` table records `(order_id, step, status, updated_at, failure_reason)`.
  * Explicit state machine transitions: `STARTED` → `STOCK_RESERVED` → `PAYMENT_COMPLETED` → `CONFIRMED`.
  * Deterministic compensation: On `payment.failed`, orchestrator emits `stock.released`, waits for stock confirmation, and marks order `CANCELLED`.
  * Easy timeout handling: A background scheduler (`SagaTimeoutScheduler`) identifies stalled sagas and triggers compensation automatically.
* *Cons*:
  * Additional coordinator logic in `order-service`.

## Decision
We chose **Saga Orchestration** managed by `OrderSagaOrchestrator` in `order-service`, persisting state in a dedicated `saga_state` relational table.

## Sequence Topology
```
[Order Created] ──> [Reserve Stock] ──> [Stock Reserved] ──> [Process Payment] ──> [Payment Completed] ──> [Confirm Order]
                                                                      │
                                                               (Payment Failed)
                                                                      ▼
                                                              [Release Stock] ──> [Order Cancelled]
```
