package com.simfat.backend.service.impl;

import com.simfat.backend.dto.auth.AuthTokensResponseDTO;
import com.simfat.backend.dto.auth.AuthUserDTO;
import com.simfat.backend.dto.auth.ForgotPasswordRequestDTO;
import com.simfat.backend.dto.auth.LoginRequestDTO;
import com.simfat.backend.dto.auth.LogoutRequestDTO;
import com.simfat.backend.dto.auth.RefreshTokenRequestDTO;
import com.simfat.backend.dto.auth.RegisterRequestDTO;
import com.simfat.backend.dto.auth.ResetPasswordRequestDTO;
import com.simfat.backend.dto.auth.SeedUsersResponseDTO;
import com.simfat.backend.dto.auth.SeededUserCredentialDTO;
import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.exception.ForbiddenException;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.exception.UnauthorizedException;
import com.simfat.backend.model.AppUser;
import com.simfat.backend.model.PasswordResetToken;
import com.simfat.backend.model.RefreshTokenRecord;
import com.simfat.backend.model.UserRole;
import com.simfat.backend.repository.AppUserRepository;
import com.simfat.backend.repository.PasswordResetTokenRepository;
import com.simfat.backend.repository.RefreshTokenRepository;
import com.simfat.backend.security.AuthProperties;
import com.simfat.backend.security.JwtService;
import com.simfat.backend.security.TokenPair;
import com.simfat.backend.service.AuthService;
import com.simfat.backend.service.RateLimiterService;
import com.simfat.backend.service.TurnstileService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Credenciales invalidas";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*()-_=+";

    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimiterService rateLimiterService;
    private final TurnstileService turnstileService;
    private final AuthProperties authProperties;
    private final Environment environment;

    public AuthServiceImpl(
        AppUserRepository appUserRepository,
        RefreshTokenRepository refreshTokenRepository,
        PasswordResetTokenRepository passwordResetTokenRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        RateLimiterService rateLimiterService,
        TurnstileService turnstileService,
        AuthProperties authProperties,
        Environment environment
    ) {
        this.appUserRepository = appUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiterService = rateLimiterService;
        this.turnstileService = turnstileService;
        this.authProperties = authProperties;
        this.environment = environment;
    }

    @Override
    public AuthTokensResponseDTO register(RegisterRequestDTO request, String remoteIp, String userAgent) {
        String email = normalizeEmail(request.email());
        turnstileService.validateToken(request.turnstileToken(), remoteIp);

        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("El correo ya se encuentra registrado");
        }

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.setRoles(new HashSet<>(Set.of(UserRole.USER)));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        AppUser savedUser = appUserRepository.save(user);

        TokenPair tokenPair = jwtService.generateTokenPair(savedUser);
        persistRefreshToken(savedUser.getId(), tokenPair, remoteIp, userAgent);
        return toAuthTokensResponse(savedUser, tokenPair);
    }

    @Override
    public AuthTokensResponseDTO login(LoginRequestDTO request, String remoteIp, String userAgent) {
        String email = normalizeEmail(request.email());
        rateLimiterService.validateLoginAttempt(rateLimitKey(email, remoteIp));
        turnstileService.validateToken(request.turnstileToken(), remoteIp);

        AppUser user = appUserRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE));
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        TokenPair tokenPair = jwtService.generateTokenPair(user);
        persistRefreshToken(user.getId(), tokenPair, remoteIp, userAgent);
        return toAuthTokensResponse(user, tokenPair);
    }

    @Override
    public AuthTokensResponseDTO refresh(RefreshTokenRequestDTO request, String remoteIp, String userAgent) {
        Jws<Claims> parsedRefresh;
        try {
            parsedRefresh = jwtService.parseAndValidate(request.refreshToken(), JwtService.TOKEN_TYPE_REFRESH);
        } catch (JwtException ex) {
            throw new UnauthorizedException("Sesion invalida");
        }

        String userId = parsedRefresh.getPayload().getSubject();
        String tokenId = parsedRefresh.getPayload().getId();
        RefreshTokenRecord currentRecord = refreshTokenRepository.findByTokenId(tokenId)
            .orElseThrow(() -> new UnauthorizedException("Sesion invalida"));

        if (!userId.equals(currentRecord.getUserId())
            || currentRecord.getRevokedAt() != null
            || Instant.now().isAfter(currentRecord.getExpiresAt())
            || !hash(request.refreshToken()).equals(currentRecord.getTokenHash())) {
            throw new UnauthorizedException("Sesion invalida");
        }

        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("Sesion invalida"));
        if (!user.isEnabled()) {
            throw new UnauthorizedException("Sesion invalida");
        }

        TokenPair newPair = jwtService.generateTokenPair(user);
        currentRecord.setRevokedAt(Instant.now());
        currentRecord.setReplacedByTokenId(newPair.getRefreshTokenId());
        refreshTokenRepository.save(currentRecord);
        persistRefreshToken(user.getId(), newPair, remoteIp, userAgent);

        return toAuthTokensResponse(user, newPair);
    }

    @Override
    public AuthUserDTO getCurrentUser(String userId) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return toUserDto(user);
    }

    @Override
    public void logout(String userId, LogoutRequestDTO request) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            try {
                Jws<Claims> parsedRefresh = jwtService.parseAndValidate(request.refreshToken(), JwtService.TOKEN_TYPE_REFRESH);
                if (userId.equals(parsedRefresh.getPayload().getSubject())) {
                    refreshTokenRepository.findByTokenId(parsedRefresh.getPayload().getId()).ifPresent(tokenRecord -> {
                        if (tokenRecord.getRevokedAt() == null) {
                            tokenRecord.setRevokedAt(Instant.now());
                            refreshTokenRepository.save(tokenRecord);
                        }
                    });
                }
            } catch (JwtException ignored) {
                // No revelar detalles de tokens invalidos en logout.
            }
        }
        revokeAllUserRefreshTokens(userId);
    }

    @Override
    public void forgotPassword(ForgotPasswordRequestDTO request, String remoteIp) {
        String email = normalizeEmail(request.email());
        rateLimiterService.validateForgotPasswordAttempt(rateLimitKey(email, remoteIp));
        turnstileService.validateToken(request.turnstileToken(), remoteIp);

        appUserRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            invalidateAllResetTokens(user.getId());
            String rawToken = generateUrlSafeToken(48);
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUserId(user.getId());
            resetToken.setTokenHash(hash(rawToken));
            resetToken.setCreatedAt(Instant.now());
            resetToken.setExpiresAt(Instant.now().plus(authProperties.getPasswordReset().getTtlMinutes(), ChronoUnit.MINUTES));
            passwordResetTokenRepository.save(resetToken);
        });
    }

    @Override
    public void resetPassword(ResetPasswordRequestDTO request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hash(request.token()))
            .orElseThrow(() -> new BadRequestException("Token de recuperacion invalido o expirado"));

        if (resetToken.getConsumedAt() != null || Instant.now().isAfter(resetToken.getExpiresAt())) {
            throw new BadRequestException("Token de recuperacion invalido o expirado");
        }

        AppUser user = appUserRepository.findById(resetToken.getUserId())
            .orElseThrow(() -> new BadRequestException("Token de recuperacion invalido o expirado"));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(Instant.now());
        appUserRepository.save(user);

        resetToken.setConsumedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);
        revokeAllUserRefreshTokens(user.getId());
    }

    @Override
    public SeedUsersResponseDTO seedUsers(int count) {
        if (!environment.acceptsProfiles(Profiles.of("dev", "local"))) {
            throw new ForbiddenException("Endpoint disponible solo en entornos de desarrollo");
        }

        List<SeededUserCredentialDTO> generatedUsers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String suffix = Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8);
            String email = "seed+" + suffix + "@simfat.local";
            String password = generateStrongPassword();
            String fullName = "Seed User " + (i + 1);

            AppUser user = new AppUser();
            user.setEmail(email.toLowerCase(Locale.ROOT));
            user.setFullName(fullName);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setEnabled(true);
            user.setRoles(new HashSet<>(Set.of(UserRole.USER)));
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);

            generatedUsers.add(new SeededUserCredentialDTO(email, password, fullName));
        }
        return new SeedUsersResponseDTO(generatedUsers);
    }

    private void persistRefreshToken(String userId, TokenPair tokenPair, String remoteIp, String userAgent) {
        RefreshTokenRecord record = new RefreshTokenRecord();
        record.setTokenId(tokenPair.getRefreshTokenId());
        record.setUserId(userId);
        record.setTokenHash(hash(tokenPair.getRefreshToken()));
        record.setIssuedAt(Instant.now());
        record.setExpiresAt(jwtService.extractExpiration(tokenPair.getRefreshToken(), JwtService.TOKEN_TYPE_REFRESH));
        record.setRevokedAt(null);
        record.setCreatedByIp(remoteIp);
        record.setUserAgent(userAgent);
        refreshTokenRepository.save(record);
    }

    private void revokeAllUserRefreshTokens(String userId) {
        List<RefreshTokenRecord> activeTokens = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        Instant now = Instant.now();
        for (RefreshTokenRecord token : activeTokens) {
            token.setRevokedAt(now);
        }
        if (!activeTokens.isEmpty()) {
            refreshTokenRepository.saveAll(activeTokens);
        }
    }

    private void invalidateAllResetTokens(String userId) {
        List<PasswordResetToken> activeTokens = passwordResetTokenRepository.findByUserIdAndConsumedAtIsNull(userId);
        Instant now = Instant.now();
        for (PasswordResetToken token : activeTokens) {
            token.setConsumedAt(now);
        }
        if (!activeTokens.isEmpty()) {
            passwordResetTokenRepository.saveAll(activeTokens);
        }
    }

    private AuthTokensResponseDTO toAuthTokensResponse(AppUser user, TokenPair pair) {
        return new AuthTokensResponseDTO(
            toUserDto(user),
            pair.getAccessToken(),
            pair.getRefreshToken(),
            pair.getAccessExpiresAt()
        );
    }

    private AuthUserDTO toUserDto(AppUser user) {
        return new AuthUserDTO(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getRoles().stream().map(Enum::name).collect(Collectors.toSet())
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String rateLimitKey(String email, String ip) {
        String normalizedIp = (ip == null || ip.isBlank()) ? "unknown" : ip;
        return normalizedIp + "|" + email;
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No fue posible hashear token", ex);
        }
    }

    private String generateUrlSafeToken(int bytes) {
        byte[] randomBytes = new byte[bytes];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String generateStrongPassword() {
        int length = 16;
        StringBuilder password = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = SECURE_RANDOM.nextInt(PASSWORD_CHARS.length());
            password.append(PASSWORD_CHARS.charAt(index));
        }
        if (!password.toString().matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{12,72}$")) {
            return generateStrongPassword();
        }
        return password.toString();
    }
}

