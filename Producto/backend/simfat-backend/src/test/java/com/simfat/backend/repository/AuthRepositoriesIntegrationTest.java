package com.simfat.backend.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.simfat.backend.model.AppUser;
import com.simfat.backend.model.PasswordResetToken;
import com.simfat.backend.model.RefreshTokenRecord;
import com.simfat.backend.model.UserRole;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = "spring.flyway.enabled=false")
class AuthRepositoriesIntegrationTest {

    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @BeforeEach
    void cleanCollections() {
        appUserRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
    }

    @Test
    void appUserRepository_enforcesUniqueEmail() {
        AppUser first = buildUser("user@example.com");
        appUserRepository.save(first);

        AppUser duplicate = buildUser("user@example.com");
        assertThrows(DataIntegrityViolationException.class, () -> appUserRepository.saveAndFlush(duplicate));
    }

    @Test
    void refreshTokenRepository_enforcesUniqueTokenId() {
        RefreshTokenRecord first = new RefreshTokenRecord();
        first.setTokenId("rt-1");
        first.setUserId("user-1");
        first.setTokenHash("hash-1");
        first.setIssuedAt(Instant.now());
        first.setExpiresAt(Instant.now().plusSeconds(3600));
        refreshTokenRepository.save(first);

        RefreshTokenRecord duplicate = new RefreshTokenRecord();
        duplicate.setTokenId("rt-1");
        duplicate.setUserId("user-2");
        duplicate.setTokenHash("hash-2");
        duplicate.setIssuedAt(Instant.now());
        duplicate.setExpiresAt(Instant.now().plusSeconds(3600));

        assertThrows(DataIntegrityViolationException.class, () -> refreshTokenRepository.saveAndFlush(duplicate));
    }

    @Test
    void passwordResetRepository_enforcesUniqueTokenHash() {
        PasswordResetToken first = new PasswordResetToken();
        first.setUserId("user-1");
        first.setTokenHash("reset-hash-1");
        first.setCreatedAt(Instant.now());
        first.setExpiresAt(Instant.now().plusSeconds(1200));
        passwordResetTokenRepository.save(first);

        PasswordResetToken duplicate = new PasswordResetToken();
        duplicate.setUserId("user-2");
        duplicate.setTokenHash("reset-hash-1");
        duplicate.setCreatedAt(Instant.now());
        duplicate.setExpiresAt(Instant.now().plusSeconds(1200));

        assertThrows(DataIntegrityViolationException.class, () -> passwordResetTokenRepository.saveAndFlush(duplicate));
    }

    private AppUser buildUser(String email) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFullName("Test");
        user.setPasswordHash("hash");
        user.setEnabled(true);
        user.setRoles(Set.of(UserRole.USER));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }
}
