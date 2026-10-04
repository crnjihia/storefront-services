package com.storefront.notificationservice.service;

import com.storefront.notificationservice.domain.Notification;
import com.storefront.notificationservice.domain.NotificationChannel;
import com.storefront.notificationservice.domain.NotificationStatus;
import com.storefront.notificationservice.repository.NotificationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SimulatedNotificationSender implements NotificationSender {

    private final NotificationRepository notificationRepository;

    @Override
    public Notification send(UUID userId, NotificationChannel channel, String recipient, String message) {
        // Simulated send: log and persist as SENT
        log.info("Simulated {} notification to {} for user {}: {}", channel, recipient, userId, message);
        Notification notification = Notification.builder()
                .userId(userId)
                .channel(channel)
                .recipient(recipient)
                .message(message)
                .status(NotificationStatus.SENT)
                .build();
        return notificationRepository.save(notification);
    }
}
