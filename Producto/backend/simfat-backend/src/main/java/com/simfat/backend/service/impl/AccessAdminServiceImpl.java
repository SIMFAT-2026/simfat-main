package com.simfat.backend.service.impl;

import com.simfat.backend.dto.admin.AccessPermissionDTO;
import com.simfat.backend.dto.admin.AccessRoleDTO;
import com.simfat.backend.dto.admin.AccessUserDTO;
import com.simfat.backend.dto.admin.CommunityChatAccessDTO;
import com.simfat.backend.dto.admin.UpdateCommunityChatAccessRequestDTO;
import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.model.AppPermission;
import com.simfat.backend.model.AppRole;
import com.simfat.backend.model.AppUser;
import com.simfat.backend.model.CommunityChatRoomAccess;
import com.simfat.backend.model.UserCommunityProfile;
import com.simfat.backend.model.UserRole;
import com.simfat.backend.model.UserRoleAssignment;
import com.simfat.backend.model.UserRoleAssignmentId;
import com.simfat.backend.model.UserVerification;
import com.simfat.backend.repository.AppPermissionRepository;
import com.simfat.backend.repository.AppRoleRepository;
import com.simfat.backend.repository.AppUserRepository;
import com.simfat.backend.repository.CommunityChatRoomAccessRepository;
import com.simfat.backend.repository.UserCommunityProfileRepository;
import com.simfat.backend.repository.UserRoleAssignmentRepository;
import com.simfat.backend.repository.UserVerificationRepository;
import com.simfat.backend.service.AccessAdminService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessAdminServiceImpl implements AccessAdminService {

    private final AppUserRepository appUserRepository;
    private final AppRoleRepository appRoleRepository;
    private final AppPermissionRepository appPermissionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final UserVerificationRepository userVerificationRepository;
    private final UserCommunityProfileRepository userCommunityProfileRepository;
    private final CommunityChatRoomAccessRepository communityChatRoomAccessRepository;

    public AccessAdminServiceImpl(
        AppUserRepository appUserRepository,
        AppRoleRepository appRoleRepository,
        AppPermissionRepository appPermissionRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        UserVerificationRepository userVerificationRepository,
        UserCommunityProfileRepository userCommunityProfileRepository,
        CommunityChatRoomAccessRepository communityChatRoomAccessRepository
    ) {
        this.appUserRepository = appUserRepository;
        this.appRoleRepository = appRoleRepository;
        this.appPermissionRepository = appPermissionRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.userVerificationRepository = userVerificationRepository;
        this.userCommunityProfileRepository = userCommunityProfileRepository;
        this.communityChatRoomAccessRepository = communityChatRoomAccessRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccessUserDTO> getUsers() {
        List<AppUser> users = appUserRepository.findAllByOrderByCreatedAtDesc();
        Map<String, Set<String>> assignedRoleCodesByUser = resolveAssignedRoleCodesByUser(users);
        Map<String, UserVerification> verificationByUser = userVerificationRepository.findAll().stream()
            .collect(Collectors.toMap(UserVerification::getUserId, item -> item));
        Map<String, UserCommunityProfile> profileByUser = userCommunityProfileRepository.findAll().stream()
            .collect(Collectors.toMap(UserCommunityProfile::getUserId, item -> item));
        Map<String, Set<String>> additionalRegionsByUser = resolveAdditionalRegionsByUser(users);

        return users.stream().map(user -> toAccessUserDTO(
            user,
            assignedRoleCodesByUser.getOrDefault(user.getId(), Set.of()),
            verificationByUser.get(user.getId()),
            profileByUser.get(user.getId()),
            additionalRegionsByUser.getOrDefault(user.getId(), Set.of())
        )).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccessRoleDTO> getRoles() {
        return appRoleRepository.findAll().stream()
            .sorted(Comparator.comparing(AppRole::getCode))
            .map(role -> new AccessRoleDTO(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.isSystem()
            ))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccessPermissionDTO> getPermissions() {
        return appPermissionRepository.findAll().stream()
            .sorted(Comparator.comparing(AppPermission::getModule).thenComparing(AppPermission::getCode))
            .map(permission -> new AccessPermissionDTO(
                permission.getCode(),
                permission.getName(),
                permission.getModule(),
                permission.getDescription()
            ))
            .toList();
    }

    @Override
    @Transactional
    public AccessUserDTO updateUserRoles(String targetUserId, Set<String> requestedRoleCodes, String actorUserId) {
        AppUser user = appUserRepository.findById(targetUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Set<String> sanitizedCodes = requestedRoleCodes == null
            ? Set.of()
            : requestedRoleCodes.stream().filter(code -> code != null && !code.isBlank()).collect(Collectors.toSet());

        List<AppRole> roles = sanitizedCodes.isEmpty()
            ? List.of()
            : appRoleRepository.findByCodeIn(sanitizedCodes);

        Set<String> foundCodes = roles.stream().map(AppRole::getCode).collect(Collectors.toSet());
        if (!foundCodes.equals(sanitizedCodes)) {
            Set<String> missing = new HashSet<>(sanitizedCodes);
            missing.removeAll(foundCodes);
            throw new BadRequestException("Roles invalidos: " + String.join(",", missing));
        }

        userRoleAssignmentRepository.deleteByIdUserId(user.getId());
        if (!roles.isEmpty()) {
            List<UserRoleAssignment> assignments = new ArrayList<>();
            Instant now = Instant.now();
            for (AppRole role : roles) {
                UserRoleAssignment item = new UserRoleAssignment();
                item.setId(new UserRoleAssignmentId(user.getId(), role.getId()));
                item.setAssignedBy(actorUserId);
                item.setAssignedAt(now);
                assignments.add(item);
            }
            userRoleAssignmentRepository.saveAll(assignments);
        }

        return toAccessUserDTO(
            user,
            foundCodes,
            userVerificationRepository.findById(user.getId()).orElse(null),
            userCommunityProfileRepository.findById(user.getId()).orElse(null),
            resolveAdditionalRegions(user.getId())
        );
    }

    @Override
    @Transactional
    public AccessUserDTO updateCommunityChatAccess(
        String targetUserId,
        UpdateCommunityChatAccessRequestDTO request,
        String actorUserId
    ) {
        AppUser user = appUserRepository.findById(targetUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        UserCommunityProfile profile = userCommunityProfileRepository.findById(user.getId()).orElseGet(UserCommunityProfile::new);
        profile.setUserId(user.getId());
        profile.setPrimaryRegionId(normalizeRegionId(request == null ? null : request.primaryRegionId()));
        UserCommunityProfile savedProfile = userCommunityProfileRepository.save(profile);

        List<CommunityChatRoomAccess> activeGrants = communityChatRoomAccessRepository.findByUserIdAndRevokedAtIsNull(user.getId());
        if (!activeGrants.isEmpty()) {
            Instant revokedAt = Instant.now();
            activeGrants.forEach(access -> access.setRevokedAt(revokedAt));
            communityChatRoomAccessRepository.saveAll(activeGrants);
        }

        Set<String> additionalRegions = sanitizeRegionIds(request == null ? Set.of() : request.additionalRegionIds());
        if (!additionalRegions.isEmpty()) {
            List<CommunityChatRoomAccess> grants = additionalRegions.stream().map(regionId -> {
                CommunityChatRoomAccess access = new CommunityChatRoomAccess();
                access.setUserId(user.getId());
                access.setRegionId(regionId);
                access.setGrantedBy(actorUserId);
                access.setGrantedAt(Instant.now());
                return access;
            }).toList();
            communityChatRoomAccessRepository.saveAll(grants);
        }

        return toAccessUserDTO(
            user,
            resolveAssignedRoleCodesByUser(List.of(user)).getOrDefault(user.getId(), Set.of()),
            userVerificationRepository.findById(user.getId()).orElse(null),
            savedProfile,
            resolveAdditionalRegions(user.getId())
        );
    }

    private Map<String, Set<String>> resolveAssignedRoleCodesByUser(List<AppUser> users) {
        Set<String> userIds = users.stream().map(AppUser::getId).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<String, Set<String>> roleIdsByUser = new HashMap<>();
        for (UserRoleAssignment assignment : userRoleAssignmentRepository.findAll()) {
            String userId = assignment.getId().getUserId();
            if (!userIds.contains(userId)) {
                continue;
            }
            roleIdsByUser.computeIfAbsent(userId, key -> new LinkedHashSet<>()).add(assignment.getId().getRoleId());
        }

        Set<String> allRoleIds = roleIdsByUser.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        if (allRoleIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> roleCodeById = appRoleRepository.findAllById(allRoleIds).stream()
            .collect(Collectors.toMap(AppRole::getId, AppRole::getCode));

        Map<String, Set<String>> roleCodesByUser = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : roleIdsByUser.entrySet()) {
            Set<String> codes = entry.getValue().stream()
                .map(roleCodeById::get)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
            roleCodesByUser.put(entry.getKey(), codes);
        }

        return roleCodesByUser;
    }

    private Map<String, Set<String>> resolveAdditionalRegionsByUser(List<AppUser> users) {
        Map<String, Set<String>> result = new HashMap<>();
        for (AppUser user : users) {
            result.put(user.getId(), resolveAdditionalRegions(user.getId()));
        }
        return result;
    }

    private Set<String> resolveAdditionalRegions(String userId) {
        return communityChatRoomAccessRepository.findByUserIdAndRevokedAtIsNull(userId).stream()
            .map(CommunityChatRoomAccess::getRegionId)
            .filter(regionId -> regionId != null && !regionId.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private AccessUserDTO toAccessUserDTO(
        AppUser user,
        Set<String> assignedRoleCodes,
        UserVerification verification,
        UserCommunityProfile profile,
        Set<String> additionalRegionIds
    ) {
        Set<String> legacyRoles = user.getRoles() == null
            ? Set.of()
            : user.getRoles().stream().map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> effectiveRoles = new LinkedHashSet<>();
        if (assignedRoleCodes != null && !assignedRoleCodes.isEmpty()) {
            effectiveRoles.addAll(assignedRoleCodes);
        } else {
            Set<UserRole> userLegacyRoles = user.getRoles() == null ? Set.of() : user.getRoles();
            for (UserRole legacyRole : userLegacyRoles) {
                if (legacyRole == UserRole.ADMIN) {
                    effectiveRoles.add("ROLE_ADMIN");
                } else {
                    effectiveRoles.add("ROLE_COMMUNITY_USER");
                }
            }
        }

        String verificationStatus = verification == null || verification.getStatus() == null
            ? "UNVERIFIED"
            : verification.getStatus().name();

        return new AccessUserDTO(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.isEnabled(),
            legacyRoles,
            assignedRoleCodes == null ? Set.of() : assignedRoleCodes,
            effectiveRoles,
            verificationStatus,
            new CommunityChatAccessDTO(
                profile == null ? "" : profile.getPrimaryRegionId(),
                additionalRegionIds == null ? Set.of() : additionalRegionIds
            )
        );
    }

    private Set<String> sanitizeRegionIds(Set<String> rawRegionIds) {
        if (rawRegionIds == null || rawRegionIds.isEmpty()) {
            return Set.of();
        }
        return rawRegionIds.stream()
            .map(this::normalizeRegionId)
            .filter(regionId -> regionId != null && !regionId.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeRegionId(String rawRegionId) {
        if (rawRegionId == null || rawRegionId.isBlank()) {
            return "";
        }
        return rawRegionId.trim().toLowerCase();
    }
}
