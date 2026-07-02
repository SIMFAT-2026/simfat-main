package com.simfat.backend.service.impl;

import com.simfat.backend.dto.admin.AccessPermissionDTO;
import com.simfat.backend.dto.admin.AccessRoleDTO;
import com.simfat.backend.dto.admin.AccessUserDTO;
import com.simfat.backend.dto.admin.CommunityChatAccessDTO;
import com.simfat.backend.dto.admin.PendingReviewUserDTO;
import com.simfat.backend.dto.admin.UpdateCommunityChatAccessRequestDTO;
import com.simfat.backend.dto.admin.UpdateVerificationStatusRequestDTO;
import com.simfat.backend.dto.admin.VerificationEventDTO;
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
import com.simfat.backend.model.VerificationEvent;
import com.simfat.backend.model.VerificationStatus;
import com.simfat.backend.repository.AppPermissionRepository;
import com.simfat.backend.repository.AppRoleRepository;
import com.simfat.backend.repository.AppUserRepository;
import com.simfat.backend.repository.CommunityChatRoomAccessRepository;
import com.simfat.backend.repository.UserCommunityProfileRepository;
import com.simfat.backend.repository.UserRoleAssignmentRepository;
import com.simfat.backend.repository.UserVerificationRepository;
import com.simfat.backend.repository.VerificationEventRepository;
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
    private final VerificationEventRepository verificationEventRepository;

    public AccessAdminServiceImpl(
        AppUserRepository appUserRepository,
        AppRoleRepository appRoleRepository,
        AppPermissionRepository appPermissionRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        UserVerificationRepository userVerificationRepository,
        UserCommunityProfileRepository userCommunityProfileRepository,
        CommunityChatRoomAccessRepository communityChatRoomAccessRepository,
        VerificationEventRepository verificationEventRepository
    ) {
        this.appUserRepository = appUserRepository;
        this.appRoleRepository = appRoleRepository;
        this.appPermissionRepository = appPermissionRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.userVerificationRepository = userVerificationRepository;
        this.userCommunityProfileRepository = userCommunityProfileRepository;
        this.communityChatRoomAccessRepository = communityChatRoomAccessRepository;
        this.verificationEventRepository = verificationEventRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccessUserDTO> getUsers() {
        List<AppUser> users = appUserRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(AppUser::isEnabled).toList();
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

    @Override
    @Transactional(readOnly = true)
    public List<VerificationEventDTO> getVerificationEvents(String userId) {
        appUserRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return verificationEventRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toVerificationEventDTO)
            .toList();
    }

    @Override
    @Transactional
    public AccessUserDTO updateVerificationStatus(String targetUserId, UpdateVerificationStatusRequestDTO request, String actorUserId) {
        AppUser user = appUserRepository.findById(targetUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        VerificationStatus newStatus;
        try {
            newStatus = VerificationStatus.valueOf(request.newStatus());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Estado de verificacion invalido: " + request.newStatus());
        }

        UserVerification verification = userVerificationRepository.findById(targetUserId)
            .orElseGet(() -> {
                UserVerification v = new UserVerification();
                v.setUserId(targetUserId);
                return v;
            });

        VerificationStatus oldStatus = verification.getStatus();
        verification.setStatus(newStatus);
        userVerificationRepository.save(verification);

        VerificationEvent event = new VerificationEvent();
        event.setUserId(targetUserId);
        event.setEventType("ADMIN_STATUS_CHANGE");
        event.setOldStatus(oldStatus);
        event.setNewStatus(newStatus);
        event.setReviewedBy(actorUserId);
        event.setNotes(request.notes());
        verificationEventRepository.save(event);

        syncVerifiedRole(targetUserId, newStatus, actorUserId);

        return toAccessUserDTO(
            user,
            resolveAssignedRoleCodesByUser(List.of(user)).getOrDefault(user.getId(), Set.of()),
            verification,
            userCommunityProfileRepository.findById(user.getId()).orElse(null),
            resolveAdditionalRegions(user.getId())
        );
    }

    private void syncVerifiedRole(String userId, VerificationStatus newStatus, String actorUserId) {
        AppRole verifiedRole = appRoleRepository.findByCode("ROLE_VERIFIED_USER").orElse(null);
        if (verifiedRole == null) return;

        boolean shouldBeVerified = newStatus == VerificationStatus.IDENTITY_VERIFIED
            || newStatus == VerificationStatus.FULLY_VERIFIED;

        UserRoleAssignmentId assignmentId = new UserRoleAssignmentId(userId, verifiedRole.getId());
        boolean alreadyAssigned = userRoleAssignmentRepository.existsById(assignmentId);

        if (shouldBeVerified && !alreadyAssigned) {
            UserRoleAssignment assignment = new UserRoleAssignment();
            assignment.setId(assignmentId);
            assignment.setAssignedBy(actorUserId);
            assignment.setAssignedAt(Instant.now());
            userRoleAssignmentRepository.save(assignment);
        } else if (!shouldBeVerified && alreadyAssigned) {
            userRoleAssignmentRepository.deleteById(assignmentId);
        }
    }

    @Override
    @Transactional
    public void deleteUser(String targetUserId, String actorUserId) {
        if (targetUserId.equals(actorUserId)) {
            throw new BadRequestException("No puedes eliminar tu propia cuenta");
        }
        AppUser user = appUserRepository.findById(targetUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        appRoleRepository.findByCode("ROLE_SUPER_ADMIN").ifPresent(superAdminRole -> {
            boolean targetIsSuperAdmin = userRoleAssignmentRepository.findByIdUserId(targetUserId).stream()
                .anyMatch(a -> superAdminRole.getId().equals(a.getId().getRoleId()));
            if (targetIsSuperAdmin) {
                throw new BadRequestException("No se puede eliminar a otro Super Admin");
            }
        });

        user.setEnabled(false);
        appUserRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingReviewUserDTO> getPendingReview() {
        List<VerificationEvent> pendingEvents = verificationEventRepository.findPendingIdentityResets();
        return pendingEvents.stream()
            .map(event -> {
                AppUser user = appUserRepository.findById(event.getUserId()).orElse(null);
                if (user == null) return null;
                UserVerification verification = userVerificationRepository.findById(event.getUserId()).orElse(null);
                String currentStatus = verification != null && verification.getStatus() != null
                    ? verification.getStatus().name()
                    : "UNVERIFIED";
                return new PendingReviewUserDTO(
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    currentStatus,
                    toVerificationEventDTO(event)
                );
            })
            .filter(dto -> dto != null)
            .toList();
    }

    private VerificationEventDTO toVerificationEventDTO(VerificationEvent event) {
        return new VerificationEventDTO(
            event.getId(),
            event.getEventType(),
            event.getOldStatus() != null ? event.getOldStatus().name() : null,
            event.getNewStatus() != null ? event.getNewStatus().name() : null,
            event.getReviewedBy(),
            event.getNotes(),
            event.getCreatedAt()
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

        String organizationName = verification != null ? verification.getOrganizationName() : null;

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
            ),
            user.getPhone(),
            user.getRegionCode(),
            user.getComunaCode(),
            organizationName
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
