package com.simfat.backend.service;

public interface TurnstileService {

    void validateToken(String token, String remoteIp);
}

