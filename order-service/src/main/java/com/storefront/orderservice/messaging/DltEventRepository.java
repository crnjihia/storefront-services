package com.storefront.orderservice.messaging;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DltEventRepository extends JpaRepository<DltEvent, UUID> {
    // No custom methods required for now
}
