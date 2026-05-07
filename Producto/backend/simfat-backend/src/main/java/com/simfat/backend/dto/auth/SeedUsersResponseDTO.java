package com.simfat.backend.dto.auth;

import java.util.List;

public record SeedUsersResponseDTO(
    List<SeededUserCredentialDTO> users
) {
}

