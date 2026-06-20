package com.simfat.backend.dto.admin;

public record PendingReviewUserDTO(
    String id,
    String email,
    String fullName,
    String currentStatus,
    VerificationEventDTO lastEvent
) {
}
