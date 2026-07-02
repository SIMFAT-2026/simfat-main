package com.simfat.backend.service;

import com.simfat.backend.dto.admin.AccessPermissionDTO;
import com.simfat.backend.dto.admin.AccessRoleDTO;
import com.simfat.backend.dto.admin.AccessUserDTO;
import com.simfat.backend.dto.admin.PendingReviewUserDTO;
import com.simfat.backend.dto.admin.UpdateCommunityChatAccessRequestDTO;
import com.simfat.backend.dto.admin.UpdateVerificationStatusRequestDTO;
import com.simfat.backend.dto.admin.VerificationEventDTO;
import java.util.List;
import java.util.Set;

public interface AccessAdminService {

    List<AccessUserDTO> getUsers();

    List<AccessRoleDTO> getRoles();

    List<AccessPermissionDTO> getPermissions();

    AccessUserDTO updateUserRoles(String targetUserId, Set<String> requestedRoleCodes, String actorUserId);

    AccessUserDTO updateCommunityChatAccess(String targetUserId, UpdateCommunityChatAccessRequestDTO request, String actorUserId);

    List<VerificationEventDTO> getVerificationEvents(String userId);

    AccessUserDTO updateVerificationStatus(String targetUserId, UpdateVerificationStatusRequestDTO request, String actorUserId);

    List<PendingReviewUserDTO> getPendingReview();

    void deleteUser(String targetUserId, String actorUserId);
}
