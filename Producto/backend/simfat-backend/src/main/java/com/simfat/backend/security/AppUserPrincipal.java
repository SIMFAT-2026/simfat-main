package com.simfat.backend.security;

import com.simfat.backend.model.UserRole;
import java.util.Set;

public class AppUserPrincipal {

    private final String userId;
    private final String email;
    private final String fullName;
    private final Set<UserRole> roles;

    public AppUserPrincipal(String userId, String email, String fullName, Set<UserRole> roles) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.roles = roles;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public Set<UserRole> getRoles() {
        return roles;
    }
}

