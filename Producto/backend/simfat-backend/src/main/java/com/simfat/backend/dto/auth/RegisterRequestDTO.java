package com.simfat.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
    String fullName,

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no es valido")
    @Size(max = 180, message = "El correo es demasiado largo")
    String email,

    @NotBlank(message = "La contrasena es obligatoria")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{12,72}$",
        message = "La contrasena debe tener 12-72 caracteres, mayuscula, minuscula, numero y simbolo"
    )
    String password,

    String turnstileToken
) {
}

