package com.storefront.orderservice.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "saga_state")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SagaState {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(nullable = false)
    private String step; // SagaStep enum name

    @Column(nullable = false)
    private String status; // e.g., IN_PROGRESS, SUCCESS, FAILED

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "failure_reason")
    private String failureReason;
}
