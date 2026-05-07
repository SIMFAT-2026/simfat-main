package com.simfat.backend.dto.auth;

import jakarta.validation.constraints.Size;

public record LogoutRequestDTO(
    @Size(max = 5000, message = "Refresh token invalido")
    String refreshToken
) {
}

