package com.storefront.notificationservice.repository;

import com.storefront.notificationservice.domain.Notification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    // Additional query methods if needed
}
