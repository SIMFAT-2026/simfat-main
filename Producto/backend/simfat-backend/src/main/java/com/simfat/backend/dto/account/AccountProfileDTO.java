package com.simfat.backend.dto.account;

import java.time.Instant;
import java.util.Set;

public record AccountProfileDTO(
    String id,
    String email,
    String fullName,
    String phone,
    String regionCode,
    String comunaCode,
    String organizationName,
    String verificationStatus,
    Set<String> roles,
    Instant createdAt
) {
}
