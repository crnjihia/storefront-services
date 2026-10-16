package com.storefront.orderservice.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dlt_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DltEvent {

    @Id
    @GeneratedValue
    private UUID id;

    private String topic;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String payload;

    private String error;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
