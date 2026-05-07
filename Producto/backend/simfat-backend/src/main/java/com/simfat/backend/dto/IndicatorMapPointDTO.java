package com.simfat.backend.dto;

import java.time.LocalDateTime;

public class IndicatorMapPointDTO {

    private String regionId;
    private LocalDateTime observedAt;
    private Double value;
    private String aoi;
    private String quality;

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public LocalDateTime getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(LocalDateTime observedAt) {
        this.observedAt = observedAt;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public String getAoi() {
        return aoi;
    }

    public void setAoi(String aoi) {
        this.aoi = aoi;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }
}
