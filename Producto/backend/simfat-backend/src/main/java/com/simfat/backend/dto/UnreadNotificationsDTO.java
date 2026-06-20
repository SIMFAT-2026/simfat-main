package com.simfat.backend.dto;

import java.util.List;

public record UnreadNotificationsDTO(
    List<NotificationDTO> notifications,
    long unreadCount
) {
}
