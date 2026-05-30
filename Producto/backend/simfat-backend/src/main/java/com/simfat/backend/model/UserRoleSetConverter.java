package com.simfat.backend.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class UserRoleSetConverter implements AttributeConverter<Set<UserRole>, String> {

    @Override
    public String convertToDatabaseColumn(Set<UserRole> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";
        }
        return attribute.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    @Override
    public Set<UserRole> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptySet();
        }
        Set<UserRole> roles = new LinkedHashSet<>();
        for (String rawValue : dbData.split(",")) {
            UserRole role = toLegacyRole(rawValue);
            if (role != null) {
                roles.add(role);
            }
        }
        return roles;
    }

    private UserRole toLegacyRole(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return switch (rawValue.trim()) {
            case "ADMIN", "ROLE_ADMIN", "ROLE_SUPER_ADMIN" -> UserRole.ADMIN;
            case "USER", "ROLE_USER", "ROLE_COMMUNITY_USER", "ROLE_VERIFIED_USER", "ROLE_MODERATOR" -> UserRole.USER;
            default -> null;
        };
    }
}

