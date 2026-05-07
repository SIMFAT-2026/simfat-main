package com.simfat.backend.dto;

import java.time.LocalDateTime;

public class DataFreshnessDTO {

    private String regionId;
    private LocalDateTime lastUpdate;
    private Long ageSeconds;
    private DataFreshnessStatus status;

    // Backward compatibility fields.
    private Long dataFreshnessSeconds;
    private LocalDateTime computedAt;
    private boolean stale;

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public LocalDateTime getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(LocalDateTime lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public Long getAgeSeconds() {
        return ageSeconds;
    }

    public void setAgeSeconds(Long ageSeconds) {
        this.ageSeconds = ageSeconds;
    }

    public DataFreshnessStatus getStatus() {
        return status;
    }

    public void setStatus(DataFreshnessStatus status) {
        this.status = status;
    }

    public Long getDataFreshnessSeconds() {
        return dataFreshnessSeconds;
    }

    public void setDataFreshnessSeconds(Long dataFreshnessSeconds) {
        this.dataFreshnessSeconds = dataFreshnessSeconds;
    }

    public LocalDateTime getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(LocalDateTime computedAt) {
        this.computedAt = computedAt;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }
}
