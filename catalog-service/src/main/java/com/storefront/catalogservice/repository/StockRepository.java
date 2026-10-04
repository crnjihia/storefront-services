package com.storefront.catalogservice.repository;

import com.storefront.catalogservice.domain.Stock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockRepository extends JpaRepository<Stock, UUID> {
    Optional<Stock> findByProductId(UUID productId);
}
