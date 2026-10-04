package com.storefront.catalogservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {
    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false)
    private Integer available;

    @Column(nullable = false)
    private Integer reserved;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
