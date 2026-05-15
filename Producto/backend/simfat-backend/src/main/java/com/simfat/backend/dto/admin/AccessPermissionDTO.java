package com.simfat.backend.dto.admin;

public record AccessPermissionDTO(
    String code,
    String name,
    String module,
    String description
) {
}
