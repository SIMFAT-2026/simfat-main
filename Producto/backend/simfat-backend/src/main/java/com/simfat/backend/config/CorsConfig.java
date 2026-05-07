package com.simfat.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.frontend-url}")
    private String frontendUrl;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] allowedOrigins = frontendUrl.split(",");
        for (int i = 0; i < allowedOrigins.length; i++) {
            allowedOrigins[i] = allowedOrigins[i].trim();
        }

        registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);

        registry.addMapping("/uploads/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "OPTIONS")
            .allowedHeaders("*");
    }
}
