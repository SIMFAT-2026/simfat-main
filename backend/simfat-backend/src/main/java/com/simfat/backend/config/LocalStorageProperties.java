package com.simfat.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.local")
public class LocalStorageProperties {

    private boolean enabled = true;
    private String baseDir = "tmp/uploads/citizen-reports";
    private String publicBasePath = "/uploads/citizen-reports";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public String getPublicBasePath() {
        return publicBasePath;
    }

    public void setPublicBasePath(String publicBasePath) {
        this.publicBasePath = publicBasePath;
    }
}

