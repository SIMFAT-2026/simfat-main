package com.simfat.backend.controller;

import com.simfat.backend.config.OpenEoProperties;
import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.OpenEoMeasurementIngestRequestDTO;
import com.simfat.backend.dto.OpenEoMeasurementIngestResponseDTO;
import com.simfat.backend.exception.UnauthorizedException;
import com.simfat.backend.service.OpenEoIngestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/indicators")
public class OpenEoIngestController {

    private final OpenEoIngestService openEoIngestService;
    private final OpenEoProperties openEoProperties;

    public OpenEoIngestController(OpenEoIngestService openEoIngestService, OpenEoProperties openEoProperties) {
        this.openEoIngestService = openEoIngestService;
        this.openEoProperties = openEoProperties;
    }

    @PostMapping("/measurements")
    public ResponseEntity<ApiResponse<OpenEoMeasurementIngestResponseDTO>> ingestMeasurement(
        @RequestBody @Valid OpenEoMeasurementIngestRequestDTO request,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-OpenEO-Ingest-Token", required = false) String ingestToken
    ) {
        ensureIngestTokenIfConfigured(authorization, ingestToken);
        OpenEoMeasurementIngestResponseDTO response = openEoIngestService.ingestMeasurement(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.ok("Medicion openEO ingerida correctamente", response)
        );
    }

    private void ensureIngestTokenIfConfigured(String authorization, String ingestToken) {
        String expectedToken = openEoProperties.getIngest().getAuthToken();
        if (expectedToken == null || expectedToken.isBlank()) {
            return;
        }

        String bearerToken = extractBearerToken(authorization);
        if (expectedToken.equals(bearerToken) || expectedToken.equals(ingestToken)) {
            return;
        }

        throw new UnauthorizedException("Token invalido para ingesta interna de openEO");
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String prefix = "Bearer ";
        if (authorization.startsWith(prefix)) {
            return authorization.substring(prefix.length()).trim();
        }
        return null;
    }
}
