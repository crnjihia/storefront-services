package com.storefront.catalogservice.repository;

import com.storefront.catalogservice.domain.StockReservation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {
    List<StockReservation> findByOrderId(UUID orderId);
    List<StockReservation> findByOrderIdAndStatus(UUID orderId, com.storefront.catalogservice.domain.StockReservationStatus status);
}
