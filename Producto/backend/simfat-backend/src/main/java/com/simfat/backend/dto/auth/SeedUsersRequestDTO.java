package com.simfat.backend.dto.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SeedUsersRequestDTO(
    @Min(value = 1, message = "count debe ser mayor o igual a 1")
    @Max(value = 200, message = "count debe ser menor o igual a 200")
    int count
) {
}

