package com.simfat.backend.dto.auth;

import java.util.Set;

public record AuthUserDTO(
    String id,
    String email,
    String fullName,
    Set<String> roles
) {
}

