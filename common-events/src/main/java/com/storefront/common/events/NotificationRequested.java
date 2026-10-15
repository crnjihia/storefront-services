package com.storefront.common.events;

import java.util.UUID;

public record NotificationRequested(UUID orderId,
                                    UUID userId,
                                    String channel,
                                    String recipient,
                                    String message) {}
