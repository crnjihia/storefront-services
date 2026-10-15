package com.storefront.paymentservice.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {
    Optional<IdempotencyKeyEntity> findByIdempotencyKey(String idempotencyKey);
}
