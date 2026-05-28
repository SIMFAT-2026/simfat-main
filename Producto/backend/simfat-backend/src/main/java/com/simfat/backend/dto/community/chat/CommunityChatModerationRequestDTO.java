package com.simfat.backend.dto.community.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommunityChatModerationRequestDTO(
    @NotBlank(message = "La accion de moderacion es obligatoria")
    String action,
    @Size(max = 255, message = "La razon no puede exceder 255 caracteres")
    String reason
) {
}
