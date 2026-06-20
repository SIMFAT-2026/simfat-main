package com.simfat.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateVerificationStatusRequestDTO(
    @NotBlank(message = "El nuevo estado es obligatorio")
    String newStatus,

    @NotBlank(message = "Las notas son obligatorias")
    @Size(max = 255)
    String notes
) {
}
