package com.simfat.backend.service;

import com.simfat.backend.dto.admin.AccessPermissionDTO;
import com.simfat.backend.dto.admin.AccessRoleDTO;
import com.simfat.backend.dto.admin.AccessUserDTO;
import java.util.List;
import java.util.Set;

public interface AccessAdminService {

    List<AccessUserDTO> getUsers();

    List<AccessRoleDTO> getRoles();

    List<AccessPermissionDTO> getPermissions();

    AccessUserDTO updateUserRoles(String targetUserId, Set<String> requestedRoleCodes, String actorUserId);
}
