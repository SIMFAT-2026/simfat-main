package com.simfat.backend.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class TerritoryLayersResponseDTO {

    private String regionId;
    private LocalDateTime generatedAt;
    private Map<String, Object> layers;

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Map<String, Object> getLayers() {
        return layers;
    }

    public void setLayers(Map<String, Object> layers) {
        this.layers = layers;
    }
}
