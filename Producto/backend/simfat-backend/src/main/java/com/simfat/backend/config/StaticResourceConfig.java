package com.simfat.backend.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private final LocalStorageProperties localStorageProperties;

    public StaticResourceConfig(LocalStorageProperties localStorageProperties) {
        this.localStorageProperties = localStorageProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!localStorageProperties.isEnabled()) {
            return;
        }
        String basePath = normalizePublicBasePath(localStorageProperties.getPublicBasePath());
        Path absoluteBaseDir = Paths.get(localStorageProperties.getBaseDir()).toAbsolutePath().normalize();
        String location = "file:" + absoluteBaseDir.toString().replace("\\", "/") + "/";
        registry.addResourceHandler(basePath + "/**").addResourceLocations(location);
    }

    private String normalizePublicBasePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return "/uploads/citizen-reports";
        }
        String normalized = raw.startsWith("/") ? raw : "/" + raw;
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}

