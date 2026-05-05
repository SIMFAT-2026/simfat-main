package com.simfat.backend.dto.auth;

public record SeededUserCredentialDTO(
    String email,
    String password,
    String fullName
) {
}

