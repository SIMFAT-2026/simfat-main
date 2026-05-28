package com.simfat.backend.dto.community.chat;

import com.simfat.backend.model.CommunityChatPresenceState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CommunityChatPresenceRequestDTO(
    @NotBlank(message = "La sala es obligatoria")
    String roomId,
    @NotNull(message = "El estado de presencia es obligatorio")
    CommunityChatPresenceState state
) {
}
