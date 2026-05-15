package com.simfat.backend.dto.admin;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UpdateUserRolesRequestDTO(
    @NotNull(message = "El listado de roles no puede ser nulo")
    Set<String> roleCodes
) {
}
