package com.simfat.backend.security;

import java.util.Set;
import org.springframework.security.core.GrantedAuthority;

public class AuthorizationSnapshot {

    private final Set<String> roleCodes;
    private final Set<String> permissionCodes;
    private final Set<GrantedAuthority> authorities;

    public AuthorizationSnapshot(
        Set<String> roleCodes,
        Set<String> permissionCodes,
        Set<GrantedAuthority> authorities
    ) {
        this.roleCodes = roleCodes;
        this.permissionCodes = permissionCodes;
        this.authorities = authorities;
    }

    public Set<String> getRoleCodes() {
        return roleCodes;
    }

    public Set<String> getPermissionCodes() {
        return permissionCodes;
    }

    public Set<GrantedAuthority> getAuthorities() {
        return authorities;
    }
}
