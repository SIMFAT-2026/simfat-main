package com.simfat.backend.security;

import java.time.Instant;

public class TokenPair {

    private final String accessToken;
    private final String refreshToken;
    private final String refreshTokenId;
    private final Instant accessExpiresAt;

    public TokenPair(String accessToken, String refreshToken, String refreshTokenId, Instant accessExpiresAt) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.refreshTokenId = refreshTokenId;
        this.accessExpiresAt = accessExpiresAt;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getRefreshTokenId() {
        return refreshTokenId;
    }

    public Instant getAccessExpiresAt() {
        return accessExpiresAt;
    }
}

