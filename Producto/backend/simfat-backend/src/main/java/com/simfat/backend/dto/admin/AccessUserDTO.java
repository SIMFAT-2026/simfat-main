package com.simfat.backend.dto.admin;

import java.util.Set;

public record AccessUserDTO(
    String id,
    String email,
    String fullName,
    boolean enabled,
    Set<String> legacyRoles,
    Set<String> assignedRoles,
    Set<String> effectiveRoles,
    String verificationStatus,
    CommunityChatAccessDTO communityChatAccess,
    String phone,
    String regionCode,
    String comunaCode,
    String organizationName
) {
}
