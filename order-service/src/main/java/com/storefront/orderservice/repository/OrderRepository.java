package com.storefront.orderservice.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.storefront.orderservice.domain.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    // Additional query methods can be added as needed
}
