package com.simfat.backend.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    @NotNull
    private final Jwt jwt = new Jwt();

    @NotNull
    private final Turnstile turnstile = new Turnstile();

    @NotNull
    private final RateLimit rateLimit = new RateLimit();

    @NotNull
    private final PasswordReset passwordReset = new PasswordReset();

    public Jwt getJwt() {
        return jwt;
    }

    public Turnstile getTurnstile() {
        return turnstile;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public PasswordReset getPasswordReset() {
        return passwordReset;
    }

    public static class Jwt {
        @NotBlank
        private String secret;

        @Min(10)
        @Max(15)
        private int accessTtlMinutes = 15;

        @Min(1)
        @Max(60)
        private int refreshTtlDays = 14;

        @NotBlank
        private String issuer = "simfat-backend";

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public int getAccessTtlMinutes() {
            return accessTtlMinutes;
        }

        public void setAccessTtlMinutes(int accessTtlMinutes) {
            this.accessTtlMinutes = accessTtlMinutes;
        }

        public int getRefreshTtlDays() {
            return refreshTtlDays;
        }

        public void setRefreshTtlDays(int refreshTtlDays) {
            this.refreshTtlDays = refreshTtlDays;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }
    }

    public static class Turnstile {
        private boolean enabled = false;

        @NotBlank
        private String secretKey = "disabled";

        @NotBlank
        private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getVerifyUrl() {
            return verifyUrl;
        }

        public void setVerifyUrl(String verifyUrl) {
            this.verifyUrl = verifyUrl;
        }
    }

    public static class RateLimit {
        @NotNull
        private final Bucket login = new Bucket();

        @NotNull
        private final Bucket forgotPassword = new Bucket();

        public Bucket getLogin() {
            return login;
        }

        public Bucket getForgotPassword() {
            return forgotPassword;
        }
    }

    public static class Bucket {
        @Min(1)
        @Max(50)
        private int maxAttempts = 5;

        @Min(10)
        @Max(3600)
        private int windowSeconds = 300;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }

    public static class PasswordReset {
        @Min(5)
        @Max(120)
        private int ttlMinutes = 30;

        public int getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(int ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }
    }
}

