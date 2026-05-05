package com.simfat.backend.service.impl;

import com.simfat.backend.config.LocalStorageProperties;
import com.simfat.backend.exception.BadRequestException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalImageFallbackStorageService {

    private final LocalStorageProperties properties;

    public LocalImageFallbackStorageService(LocalStorageProperties properties) {
        this.properties = properties;
    }

    public String storeCitizenReportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "";
        }
        if (!properties.isEnabled()) {
            return file.getOriginalFilename() == null ? "archivo" : file.getOriginalFilename();
        }

        String folder = LocalDate.now().toString();
        String fileName = buildFileName(file.getOriginalFilename());

        try {
            Path baseDir = Paths.get(properties.getBaseDir()).toAbsolutePath().normalize();
            Path targetFolder = baseDir.resolve(folder).normalize();
            Path targetFile = targetFolder.resolve(fileName).normalize();

            if (!targetFile.startsWith(baseDir)) {
                throw new BadRequestException("Ruta de almacenamiento local invalida");
            }

            Files.createDirectories(targetFolder);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);

            return normalizePublicBasePath(properties.getPublicBasePath()) + "/" + folder + "/" + fileName;
        } catch (IOException ex) {
            throw new BadRequestException("No fue posible guardar imagen en fallback local");
        }
    }

    private String buildFileName(String originalName) {
        String normalized = originalName == null ? "imagen" : originalName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        String extension = normalized.contains(".") ? normalized.substring(normalized.lastIndexOf('.')) : ".webp";
        return UUID.randomUUID() + extension;
    }

    private String normalizePublicBasePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return "/uploads/citizen-reports";
        }
        String normalized = raw.startsWith("/") ? raw : "/" + raw;
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}

