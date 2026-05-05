package com.simfat.backend.service;

public interface RateLimiterService {

    void validateLoginAttempt(String key);

    void validateForgotPasswordAttempt(String key);
}

