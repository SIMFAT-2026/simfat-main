package com.simfat.backend.dto;

import java.time.LocalDateTime;

public class IndicatorLatestDTO {

    private String regionId;
    private String indicator;
    private LocalDateTime observedAt;
    private Double value;
    private String unit;
    private String quality;
    private String source;
    private boolean cached;

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getIndicator() {
        return indicator;
    }

    public void setIndicator(String indicator) {
        this.indicator = indicator;
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

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isCached() {
        return cached;
    }

    public void setCached(boolean cached) {
        this.cached = cached;
    }
}
