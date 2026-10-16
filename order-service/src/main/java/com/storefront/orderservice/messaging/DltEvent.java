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

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "partition_index")
    private Integer partitionIndex;

    @Column(name = "offset_index")
    private Long offsetIndex;

    @Column(name = "message_key")
    private String messageKey;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "exception_message", columnDefinition = "TEXT")
    private String error;

    @Builder.Default
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
