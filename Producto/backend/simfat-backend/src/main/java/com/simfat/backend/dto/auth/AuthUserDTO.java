package com.simfat.backend.dto.auth;

import java.util.Set;

public record AuthUserDTO(
    String id,
    String email,
    String fullName,
    // Roles legacy (enum UserRole: solo USER/ADMIN, sin SUPER_ADMIN). Se
    // mantiene por compatibilidad con consumidores existentes.
    Set<String> roles,
    // RBAC real (AuthorizationResolverService): ROLE_ADMIN, ROLE_SUPER_ADMIN,
    // ROLE_MODERATOR, etc. Es lo que el frontend debe usar para gatear UI
    // (botones admin-only), porque "roles" nunca contendra estos valores.
    Set<String> roleCodes,
    Set<String> permissionCodes
) {
}

