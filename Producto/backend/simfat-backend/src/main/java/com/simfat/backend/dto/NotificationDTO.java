package com.simfat.backend.dto;

import java.time.Instant;

public record NotificationDTO(
    String id,
    String type,
    String title,
    String message,
    String regionId,
    String comunaId,
    String alertLevel,
    boolean read,
    Instant createdAt
) {
}
