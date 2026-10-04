package com.storefront.orderservice.messaging;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    // Fetch a limited batch of unpublished events ordered by creation time
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
