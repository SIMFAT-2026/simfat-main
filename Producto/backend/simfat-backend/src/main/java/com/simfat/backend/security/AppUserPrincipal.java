package com.simfat.backend.security;

import java.util.Set;

public class AppUserPrincipal {

    private final String userId;
    private final String email;
    private final String fullName;
    private final Set<String> roleCodes;

    public AppUserPrincipal(String userId, String email, String fullName, Set<String> roleCodes) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.roleCodes = roleCodes;
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

    public Set<String> getRoleCodes() {
        return roleCodes;
    }
}

