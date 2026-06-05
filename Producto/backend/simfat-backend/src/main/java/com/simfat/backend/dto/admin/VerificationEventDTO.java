package com.simfat.backend.dto.admin;

import java.time.Instant;

public record VerificationEventDTO(
    String id,
    String eventType,
    String oldStatus,
    String newStatus,
    String reviewedBy,
    String notes,
    Instant createdAt
) {
}
