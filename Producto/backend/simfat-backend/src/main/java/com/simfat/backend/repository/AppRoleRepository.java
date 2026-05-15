package com.simfat.backend.repository;

import com.simfat.backend.model.AppRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppRoleRepository extends JpaRepository<AppRole, String> {

    Optional<AppRole> findByCode(String code);
}
