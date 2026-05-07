package com.simfat.backend.service.impl;

import com.simfat.backend.config.SupabaseStorageProperties;
import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.service.SupabaseStorageService;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SupabaseStorageServiceImpl implements SupabaseStorageService {

    private final SupabaseStorageProperties properties;
    private final RestTemplate restTemplate;

    public SupabaseStorageServiceImpl(SupabaseStorageProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @Override
    public String uploadCitizenReportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "";
        }

        if (!properties.isEnabled()) {
            return "";
        }

        validateConfig();

        try {
            String folder = LocalDate.now().toString();
            String fileName = buildFileName(file.getOriginalFilename());
            String objectPath = folder + "/" + fileName;
            String encodedPath = encodePath(objectPath);

            String uploadUrl = normalizeBaseUrl(properties.getUrl()) + "/storage/v1/object/" + properties.getBucket() + "/" + encodedPath;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(properties.getServiceKey());
            headers.set("apikey", properties.getServiceKey());
            headers.set("x-upsert", "false");
            headers.setContentType(resolveContentType(file.getContentType()));

            HttpEntity<byte[]> request = new HttpEntity<>(file.getBytes(), headers);
            ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.POST, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BadRequestException("No fue posible subir imagen a storage");
            }

            return normalizeBaseUrl(properties.getUrl()) + "/storage/v1/object/public/" + properties.getBucket() + "/" + encodedPath;
        } catch (RestClientResponseException ex) {
            throw new BadRequestException("Supabase Storage rechazo la subida (" + ex.getRawStatusCode() + ")");
        } catch (RestClientException ex) {
            throw new BadRequestException("No fue posible conectar con Supabase Storage");
        } catch (IOException ex) {
            throw new BadRequestException("No fue posible procesar la imagen para storage");
        }
    }

    private void validateConfig() {
        if (!StringUtils.hasText(properties.getUrl()) || !StringUtils.hasText(properties.getServiceKey()) || !StringUtils.hasText(properties.getBucket())) {
            throw new BadRequestException("Configuracion de Supabase Storage incompleta");
        }
        if (isPlaceholder(properties.getUrl()) || isPlaceholder(properties.getServiceKey())) {
            throw new BadRequestException("Configuracion de Supabase Storage invalida: reemplaza placeholders en SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY");
        }
    }

    private MediaType resolveContentType(String rawContentType) {
        if (!StringUtils.hasText(rawContentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(rawContentType);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String buildFileName(String originalName) {
        String normalized = originalName == null ? "imagen" : originalName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        String extension = normalized.contains(".") ? normalized.substring(normalized.lastIndexOf('.')) : ".webp";
        return UUID.randomUUID() + extension;
    }

    private String encodePath(String path) {
        return URLEncoder.encode(path, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
    }

    private String normalizeBaseUrl(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }

    private boolean isPlaceholder(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        return (normalized.startsWith("<") && normalized.endsWith(">"))
            || normalized.contains("<")
            || normalized.contains("tu-project-ref")
            || normalized.contains("service_role_key_real");
    }
}
