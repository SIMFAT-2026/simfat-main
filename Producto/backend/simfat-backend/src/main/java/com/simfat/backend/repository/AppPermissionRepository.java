package com.simfat.backend.repository;

import com.simfat.backend.model.AppPermission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppPermissionRepository extends JpaRepository<AppPermission, String> {

    Optional<AppPermission> findByCode(String code);

    List<AppPermission> findByModule(String module);
}
