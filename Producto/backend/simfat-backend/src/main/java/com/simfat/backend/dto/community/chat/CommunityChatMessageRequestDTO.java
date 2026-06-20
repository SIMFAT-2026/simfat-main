package com.simfat.backend.dto.community.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommunityChatMessageRequestDTO(
    @NotBlank(message = "El mensaje es obligatorio")
    @Size(max = 800, message = "El mensaje no puede exceder 800 caracteres")
    String content
) {
}
