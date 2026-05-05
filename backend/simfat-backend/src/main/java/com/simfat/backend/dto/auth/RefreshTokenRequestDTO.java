package com.simfat.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequestDTO(
    @NotBlank(message = "El refresh token es obligatorio")
    @Size(max = 5000, message = "Refresh token invalido")
    String refreshToken
) {
}

