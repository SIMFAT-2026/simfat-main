package com.simfat.backend.service.impl;

import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.security.AuthProperties;
import com.simfat.backend.service.TurnstileService;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class TurnstileServiceImpl implements TurnstileService {

    private final AuthProperties authProperties;
    private final RestTemplate restTemplate;

    public TurnstileServiceImpl(AuthProperties authProperties, RestTemplate restTemplate) {
        this.authProperties = authProperties;
        this.restTemplate = restTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void validateToken(String token, String remoteIp) {
        if (!authProperties.getTurnstile().isEnabled()) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Verificacion anti-bot requerida");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", authProperties.getTurnstile().getSecretKey());
        form.add("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.add("remoteip", remoteIp);
        }

        try {
            Map<String, Object> response = restTemplate.postForObject(
                authProperties.getTurnstile().getVerifyUrl(),
                form,
                Map.class
            );
            boolean success = response != null && Boolean.TRUE.equals(response.get("success"));
            if (!success) {
                throw new BadRequestException("Verificacion anti-bot invalida");
            }
        } catch (RestClientException ex) {
            throw new BadRequestException("No fue posible validar el desafio anti-bot");
        }
    }
}
