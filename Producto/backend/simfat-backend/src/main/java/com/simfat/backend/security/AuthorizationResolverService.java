package com.simfat.backend.security;

import com.simfat.backend.model.AppPermission;
import com.simfat.backend.model.AppRole;
import com.simfat.backend.model.AppUser;
import com.simfat.backend.model.RolePermission;
import com.simfat.backend.model.UserRole;
import com.simfat.backend.model.UserRoleAssignment;
import com.simfat.backend.repository.AppPermissionRepository;
import com.simfat.backend.repository.AppRoleRepository;
import com.simfat.backend.repository.RolePermissionRepository;
import com.simfat.backend.repository.UserRoleAssignmentRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationResolverService {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final AppRoleRepository appRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AppPermissionRepository appPermissionRepository;

    public AuthorizationResolverService(
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        AppRoleRepository appRoleRepository,
        RolePermissionRepository rolePermissionRepository,
        AppPermissionRepository appPermissionRepository
    ) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.appRoleRepository = appRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.appPermissionRepository = appPermissionRepository;
    }

    public AuthorizationSnapshot resolveForUser(AppUser user) {
        Set<String> roleCodes = new LinkedHashSet<>();
        roleCodes.addAll(resolveRoleCodesFromRbacTables(user.getId()));

        // Fallback transitorio para no romper usuarios legacy mientras se migra app_users.roles -> user_roles.
        if (roleCodes.isEmpty()) {
            roleCodes.addAll(resolveRoleCodesFromLegacy(user));
        }

        Set<String> permissionCodes = resolvePermissionCodesByRoleCodes(roleCodes);
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        for (String roleCode : roleCodes) {
            authorities.add(new SimpleGrantedAuthority(roleCode));
        }
        for (String permissionCode : permissionCodes) {
            authorities.add(new SimpleGrantedAuthority(permissionCode));
        }

        return new AuthorizationSnapshot(roleCodes, permissionCodes, authorities);
    }

    private static final Set<String> MODERATION_ROLE_CODES = Set.of(
        "ROLE_MODERATOR",
        "ROLE_ADMIN",
        "ROLE_SUPER_ADMIN"
    );

    public boolean hasModerationCapability(AppUser user) {
        AuthorizationSnapshot snapshot = resolveForUser(user);
        if (snapshot.getPermissionCodes().contains("PERM_REPORT_MODERATE")) {
            return true;
        }
        return snapshot.getRoleCodes().stream().anyMatch(MODERATION_ROLE_CODES::contains);
    }

    public boolean hasGlobalAdminCapability(AppUser user) {
        AuthorizationSnapshot snapshot = resolveForUser(user);
        return snapshot.getRoleCodes().contains("ROLE_ADMIN") || snapshot.getRoleCodes().contains("ROLE_SUPER_ADMIN");
    }

    private Set<String> resolveRoleCodesFromRbacTables(String userId) {
        List<UserRoleAssignment> assignments = userRoleAssignmentRepository.findByIdUserId(userId);
        if (assignments.isEmpty()) {
            return Set.of();
        }

        Set<String> roleIds = assignments.stream()
            .map(item -> item.getId().getRoleId())
            .collect(Collectors.toSet());

        return appRoleRepository.findAllById(roleIds)
            .stream()
            .map(AppRole::getCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> resolveRoleCodesFromLegacy(AppUser user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return Set.of();
        }

        Set<String> roleCodes = new LinkedHashSet<>();
        for (UserRole legacyRole : user.getRoles()) {
            if (legacyRole == UserRole.ADMIN) {
                roleCodes.add("ROLE_ADMIN");
            } else {
                roleCodes.add("ROLE_COMMUNITY_USER");
            }
        }
        return roleCodes;
    }

    private Set<String> resolvePermissionCodesByRoleCodes(Set<String> roleCodes) {
        if (roleCodes.isEmpty()) {
            return Set.of();
        }

        Set<String> roleIds = appRoleRepository.findByCodeIn(roleCodes).stream()
            .map(AppRole::getId)
            .collect(Collectors.toSet());

        if (roleIds.isEmpty()) {
            return Set.of();
        }

        Set<String> permissionIds = rolePermissionRepository.findByIdRoleIdIn(roleIds).stream()
            .map(RolePermission::getId)
            .map(id -> id.getPermissionId())
            .collect(Collectors.toSet());

        if (permissionIds.isEmpty()) {
            return Set.of();
        }

        return appPermissionRepository.findAllById(permissionIds)
            .stream()
            .map(AppPermission::getCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
