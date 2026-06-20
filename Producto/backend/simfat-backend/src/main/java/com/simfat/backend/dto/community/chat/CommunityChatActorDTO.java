package com.simfat.backend.dto.community.chat;

import java.util.Set;

public record CommunityChatActorDTO(
    String userId,
    String fullName,
    Set<String> roles
) {
}
