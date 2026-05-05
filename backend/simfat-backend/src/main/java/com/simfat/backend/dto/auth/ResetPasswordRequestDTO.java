package com.simfat.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequestDTO(
    @NotBlank(message = "El token es obligatorio")
    @Size(max = 500, message = "Token invalido")
    String token,

    @NotBlank(message = "La nueva contrasena es obligatoria")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{12,72}$",
        message = "La contrasena debe tener 12-72 caracteres, mayuscula, minuscula, numero y simbolo"
    )
    String newPassword
) {
}

