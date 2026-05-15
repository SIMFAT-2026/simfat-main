package com.simfat.backend.controller;

import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.admin.AccessPermissionDTO;
import com.simfat.backend.dto.admin.AccessRoleDTO;
import com.simfat.backend.dto.admin.AccessUserDTO;
import com.simfat.backend.dto.admin.UpdateUserRolesRequestDTO;
import com.simfat.backend.security.SecurityUtils;
import com.simfat.backend.service.AccessAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/access")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','PERM_USER_MANAGE')")
public class AccessAdminController {

    private final AccessAdminService accessAdminService;

    public AccessAdminController(AccessAdminService accessAdminService) {
        this.accessAdminService = accessAdminService;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<AccessUserDTO>>> getUsers() {
        return ResponseEntity.ok(ApiResponse.ok("Usuarios de acceso obtenidos correctamente", accessAdminService.getUsers()));
    }

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<AccessRoleDTO>>> getRoles() {
        return ResponseEntity.ok(ApiResponse.ok("Roles obtenidos correctamente", accessAdminService.getRoles()));
    }

    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<List<AccessPermissionDTO>>> getPermissions() {
        return ResponseEntity.ok(ApiResponse.ok("Permisos obtenidos correctamente", accessAdminService.getPermissions()));
    }

    @PutMapping("/users/{userId}/roles")
    public ResponseEntity<ApiResponse<AccessUserDTO>> updateUserRoles(
        @PathVariable String userId,
        @Valid @RequestBody UpdateUserRolesRequestDTO request
    ) {
        AccessUserDTO updated = accessAdminService.updateUserRoles(userId, request.roleCodes(), SecurityUtils.currentUserIdOrThrow());
        return ResponseEntity.ok(ApiResponse.ok("Roles de usuario actualizados correctamente", updated));
    }
}
