package com.simfat.backend.repository;

import com.simfat.backend.model.RolePermission;
import com.simfat.backend.model.RolePermissionId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    List<RolePermission> findByIdRoleId(String roleId);

    List<RolePermission> findByIdPermissionId(String permissionId);
}
