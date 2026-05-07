package com.simfat.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDTO(
    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no es valido")
    @Size(max = 180, message = "El correo es demasiado largo")
    String email,

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(max = 72, message = "La contrasena es demasiado larga")
    String password,

    String turnstileToken
) {
}

