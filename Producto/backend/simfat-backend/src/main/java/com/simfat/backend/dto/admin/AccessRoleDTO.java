package com.simfat.backend.dto.admin;

public record AccessRoleDTO(
    String id,
    String code,
    String name,
    String description,
    boolean system
) {
}
