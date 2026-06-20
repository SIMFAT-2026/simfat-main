package com.simfat.backend.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequestDTO(

    @NotBlank(message = "La contrasena actual es obligatoria")
    String currentPassword,

    @NotBlank(message = "La nueva contrasena es obligatoria")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{12,72}$",
        message = "La contrasena debe tener 12-72 caracteres, mayuscula, minuscula, numero y simbolo"
    )
    String newPassword,

    @NotBlank(message = "La confirmacion de contrasena es obligatoria")
    String confirmPassword
) {
}
