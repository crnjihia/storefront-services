package com.storefront.notificationservice.repository;

import com.storefront.notificationservice.domain.DeliveryUpdate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryUpdateRepository extends JpaRepository<DeliveryUpdate, UUID> {
    List<DeliveryUpdate> findByOrderIdOrderByOccurredAtAsc(UUID orderId);
}
