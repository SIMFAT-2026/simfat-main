package com.simfat.backend.repository;

import com.simfat.backend.model.UserRoleAssignment;
import com.simfat.backend.model.UserRoleAssignmentId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UserRoleAssignmentId> {

    List<UserRoleAssignment> findByIdUserId(String userId);

    List<UserRoleAssignment> findByIdRoleId(String roleId);

    void deleteByIdUserId(String userId);
}
