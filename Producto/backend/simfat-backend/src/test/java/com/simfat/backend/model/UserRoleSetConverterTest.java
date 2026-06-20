package com.simfat.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class UserRoleSetConverterTest {

    private final UserRoleSetConverter converter = new UserRoleSetConverter();

    @Test
    void convertToEntityAttribute_supportsLegacyRolePrefixedAdmin() {
        Set<UserRole> roles = converter.convertToEntityAttribute("ROLE_ADMIN");

        assertEquals(Set.of(UserRole.ADMIN), roles);
    }

    @Test
    void convertToEntityAttribute_supportsRbacCommunityRolesAsLegacyUser() {
        Set<UserRole> roles = converter.convertToEntityAttribute("ROLE_COMMUNITY_USER,ROLE_VERIFIED_USER");

        assertEquals(Set.of(UserRole.USER), roles);
    }

    @Test
    void convertToEntityAttribute_ignoresUnknownRoleInsteadOfFailingLogin() {
        Set<UserRole> roles = converter.convertToEntityAttribute("ROLE_UNKNOWN");

        assertTrue(roles.isEmpty());
    }
}
