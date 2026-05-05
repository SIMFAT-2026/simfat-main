package com.simfat.backend.security;

import com.simfat.backend.model.AppUser;
import com.simfat.backend.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final AuthProperties authProperties;
    private final SecretKey signingKey;

    public JwtService(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.signingKey = resolveSigningKey(authProperties.getJwt().getSecret());
    }

    public TokenPair generateTokenPair(AppUser user) {
        String refreshTokenId = UUID.randomUUID().toString();
        Instant accessExpiresAt = Instant.now().plus(authProperties.getJwt().getAccessTtlMinutes(), ChronoUnit.MINUTES);
        Instant refreshExpiresAt = Instant.now().plus(authProperties.getJwt().getRefreshTtlDays(), ChronoUnit.DAYS);

        String accessToken = Jwts.builder()
            .issuer(authProperties.getJwt().getIssuer())
            .subject(user.getId())
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(accessExpiresAt))
            .claim("type", TOKEN_TYPE_ACCESS)
            .claim("email", user.getEmail())
            .claim("name", user.getFullName())
            .claim("roles", toRoleStrings(user.getRoles()))
            .signWith(signingKey)
            .compact();

        String refreshToken = Jwts.builder()
            .issuer(authProperties.getJwt().getIssuer())
            .subject(user.getId())
            .id(refreshTokenId)
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(refreshExpiresAt))
            .claim("type", TOKEN_TYPE_REFRESH)
            .signWith(signingKey)
            .compact();

        return new TokenPair(accessToken, refreshToken, refreshTokenId, accessExpiresAt);
    }

    public Jws<Claims> parseAndValidate(String token, String expectedType) {
        Jws<Claims> jws = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token);
        String actualType = jws.getPayload().get("type", String.class);
        if (!expectedType.equals(actualType)) {
            throw new JwtException("Tipo de token invalido");
        }
        return jws;
    }

    public Instant extractExpiration(String token, String expectedType) {
        return parseAndValidate(token, expectedType).getPayload().getExpiration().toInstant();
    }

    public String extractSubject(String token, String expectedType) {
        return parseAndValidate(token, expectedType).getPayload().getSubject();
    }

    public String extractTokenId(String token, String expectedType) {
        return parseAndValidate(token, expectedType).getPayload().getId();
    }

    private SecretKey resolveSigningKey(String secret) {
        byte[] bytes = tryDecodeBase64(secret);
        if (bytes.length < 32) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalArgumentException("auth.jwt.secret debe tener al menos 32 bytes");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    private byte[] tryDecodeBase64(String value) {
        try {
            if (Base64.getDecoder().decode(value).length > 0) {
                return Decoders.BASE64.decode(value);
            }
            return value.getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }

    private Set<String> toRoleStrings(Set<UserRole> roles) {
        return roles.stream().map(Enum::name).collect(Collectors.toSet());
    }
}
