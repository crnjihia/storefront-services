package com.storefront.orderservice.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.storefront.orderservice.domain.SagaState;

@Repository
public interface SagaStateRepository extends JpaRepository<SagaState, UUID> {
    // Additional query methods if needed
}
