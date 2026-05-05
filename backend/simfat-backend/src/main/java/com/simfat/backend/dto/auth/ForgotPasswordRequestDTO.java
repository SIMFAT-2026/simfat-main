package com.simfat.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequestDTO(
    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no es valido")
    @Size(max = 180, message = "El correo es demasiado largo")
    String email,

    String turnstileToken
) {
}

