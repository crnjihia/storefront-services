package com.storefront.catalogservice.messaging;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
    boolean existsByIdEventIdAndIdConsumer(UUID eventId, String consumer);
}
