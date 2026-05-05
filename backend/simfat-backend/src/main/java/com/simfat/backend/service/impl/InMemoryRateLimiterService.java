package com.simfat.backend.service.impl;

import com.simfat.backend.exception.TooManyRequestsException;
import com.simfat.backend.security.AuthProperties;
import com.simfat.backend.service.RateLimiterService;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class InMemoryRateLimiterService implements RateLimiterService {

    private final AuthProperties authProperties;
    private final Map<String, Counter> loginCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> forgotCounters = new ConcurrentHashMap<>();

    public InMemoryRateLimiterService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public void validateLoginAttempt(String key) {
        validate(
            "login:" + key,
            loginCounters,
            authProperties.getRateLimit().getLogin().getMaxAttempts(),
            authProperties.getRateLimit().getLogin().getWindowSeconds(),
            "Demasiados intentos de inicio de sesion. Intenta nuevamente en unos minutos."
        );
    }

    @Override
    public void validateForgotPasswordAttempt(String key) {
        validate(
            "forgot:" + key,
            forgotCounters,
            authProperties.getRateLimit().getForgotPassword().getMaxAttempts(),
            authProperties.getRateLimit().getForgotPassword().getWindowSeconds(),
            "Demasiadas solicitudes de recuperacion. Intenta nuevamente en unos minutos."
        );
    }

    private void validate(
        String key,
        Map<String, Counter> counters,
        int maxAttempts,
        int windowSeconds,
        String message
    ) {
        Instant now = Instant.now();
        Counter counter = counters.computeIfAbsent(key, ignored -> new Counter(now.plusSeconds(windowSeconds), 0));
        synchronized (counter) {
            if (now.isAfter(counter.windowEndsAt)) {
                counter.windowEndsAt = now.plusSeconds(windowSeconds);
                counter.attempts = 0;
            }
            counter.attempts++;
            if (counter.attempts > maxAttempts) {
                throw new TooManyRequestsException(message);
            }
        }
    }

    private static class Counter {
        private Instant windowEndsAt;
        private int attempts;

        private Counter(Instant windowEndsAt, int attempts) {
            this.windowEndsAt = windowEndsAt;
            this.attempts = attempts;
        }
    }
}

