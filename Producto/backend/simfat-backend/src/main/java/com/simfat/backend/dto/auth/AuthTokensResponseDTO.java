package com.simfat.backend.dto.auth;

import java.time.Instant;

public record AuthTokensResponseDTO(
    AuthUserDTO user,
    String accessToken,
    String refreshToken,
    Instant expiresAt
) {
}

