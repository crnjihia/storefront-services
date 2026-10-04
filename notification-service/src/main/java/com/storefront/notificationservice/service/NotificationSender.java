package com.storefront.notificationservice.service;

import com.storefront.notificationservice.domain.Notification;
import com.storefront.notificationservice.domain.NotificationChannel;
import com.storefront.notificationservice.domain.NotificationStatus;
import java.util.UUID;

public interface NotificationSender {
    /**
     * Sends a notification and persists it.
     *
     * @param userId the recipient user id
     * @param channel SMS or EMAIL
     * @param recipient contact info (phone or email)
     * @param message text of the notification
     * @return the persisted Notification entity
     */
    Notification send(UUID userId, NotificationChannel channel, String recipient, String message);
}
