package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.simfat.backend.dto.auth.ForgotPasswordRequestDTO;
import com.simfat.backend.dto.auth.LoginRequestDTO;
import com.simfat.backend.dto.auth.RefreshTokenRequestDTO;
import com.simfat.backend.dto.auth.RegisterRequestDTO;
import com.simfat.backend.exception.ForbiddenException;
import com.simfat.backend.exception.UnauthorizedException;
import com.simfat.backend.model.AppUser;
import com.simfat.backend.model.RefreshTokenRecord;
import com.simfat.backend.model.UserRole;
import com.simfat.backend.repository.AppUserRepository;
import com.simfat.backend.repository.PasswordResetTokenRepository;
import com.simfat.backend.repository.RefreshTokenRepository;
import com.simfat.backend.security.AuthProperties;
import com.simfat.backend.security.JwtService;
import com.simfat.backend.security.TokenPair;
import com.simfat.backend.service.RateLimiterService;
import com.simfat.backend.service.TurnstileService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RateLimiterService rateLimiterService;
    @Mock
    private TurnstileService turnstileService;
    @Mock
    private Environment environment;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.getPasswordReset().setTtlMinutes(30);
        service = new AuthServiceImpl(
            appUserRepository,
            refreshTokenRepository,
            passwordResetTokenRepository,
            passwordEncoder,
            jwtService,
            rateLimiterService,
            turnstileService,
            authProperties,
            environment
        );
    }

    @Test
    void register_createsUserAndReturnsTokens() {
        when(appUserRepository.existsByEmailIgnoreCase("ana@example.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass!123")).thenReturn("hashed-pass");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId("user-1");
            return user;
        });
        TokenPair pair = new TokenPair("access-token", "refresh-token", "rt-1", Instant.now().plusSeconds(900));
        when(jwtService.generateTokenPair(any(AppUser.class))).thenReturn(pair);
        when(jwtService.extractExpiration("refresh-token", JwtService.TOKEN_TYPE_REFRESH)).thenReturn(Instant.now().plusSeconds(3600));

        var response = service.register(
            new RegisterRequestDTO("Ana", "Ana@example.com", "StrongPass!123", null),
            "127.0.0.1",
            "JUnit"
        );

        assertEquals("ana@example.com", response.user().email());
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertNotNull(response.expiresAt());
        verify(refreshTokenRepository).save(any(RefreshTokenRecord.class));
    }

    @Test
    void login_withInvalidPassword_throwsUnauthorized() {
        AppUser user = new AppUser();
        user.setId("user-1");
        user.setEnabled(true);
        user.setEmail("ana@example.com");
        user.setPasswordHash("hashed-pass");
        user.setRoles(Set.of(UserRole.USER));

        when(appUserRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad-pass", "hashed-pass")).thenReturn(false);

        assertThrows(
            UnauthorizedException.class,
            () -> service.login(new LoginRequestDTO("ana@example.com", "bad-pass", null), "127.0.0.1", "JUnit")
        );
    }

    @Test
    void forgotPassword_withUnknownEmail_doesNotPersistResetToken() {
        when(appUserRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        service.forgotPassword(new ForgotPasswordRequestDTO("ghost@example.com", null), "127.0.0.1");

        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void refresh_rotatesRefreshToken() {
        String oldRefresh = "old-refresh-token";
        String oldHash = sha256(oldRefresh);
        RefreshTokenRecord current = new RefreshTokenRecord();
        current.setTokenId("rt-old");
        current.setUserId("user-1");
        current.setTokenHash(oldHash);
        current.setExpiresAt(Instant.now().plusSeconds(3600));

        @SuppressWarnings("unchecked")
        Jws<Claims> jws = org.mockito.Mockito.mock(Jws.class);
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(jws.getPayload()).thenReturn(claims);
        when(claims.getSubject()).thenReturn("user-1");
        when(claims.getId()).thenReturn("rt-old");
        when(jwtService.parseAndValidate(oldRefresh, JwtService.TOKEN_TYPE_REFRESH)).thenReturn(jws);
        when(refreshTokenRepository.findByTokenId("rt-old")).thenReturn(Optional.of(current));

        AppUser user = new AppUser();
        user.setId("user-1");
        user.setEmail("ana@example.com");
        user.setFullName("Ana");
        user.setEnabled(true);
        user.setRoles(Set.of(UserRole.USER));
        when(appUserRepository.findById("user-1")).thenReturn(Optional.of(user));

        TokenPair newPair = new TokenPair("new-access", "new-refresh", "rt-new", Instant.now().plusSeconds(900));
        when(jwtService.generateTokenPair(user)).thenReturn(newPair);
        when(jwtService.extractExpiration("new-refresh", JwtService.TOKEN_TYPE_REFRESH)).thenReturn(Instant.now().plusSeconds(7200));

        var response = service.refresh(new RefreshTokenRequestDTO(oldRefresh), "127.0.0.1", "JUnit");

        assertEquals("new-access", response.accessToken());
        assertEquals("new-refresh", response.refreshToken());
        verify(refreshTokenRepository).save(eq(current));
        verify(refreshTokenRepository, times(2)).save(any(RefreshTokenRecord.class));
    }

    @Test
    void seedUsers_requiresDevOrLocalProfile() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> service.seedUsers(2));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
