package com.storefront.paymentservice.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "processed_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @EmbeddedId
    private ProcessedEventId id;

    @Builder.Default
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt = Instant.now();
}
