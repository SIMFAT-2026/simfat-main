package com.simfat.backend.dto;

import java.time.LocalDateTime;

public class SyncRunResponseDTO {

    private LocalDateTime triggeredAt;
    private int totalRegions;
    private int totalJobsAccepted;
    private int totalDeduplicated;
    private int totalErrors;

    public LocalDateTime getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(LocalDateTime triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public int getTotalRegions() {
        return totalRegions;
    }

    public void setTotalRegions(int totalRegions) {
        this.totalRegions = totalRegions;
    }

    public int getTotalJobsAccepted() {
        return totalJobsAccepted;
    }

    public void setTotalJobsAccepted(int totalJobsAccepted) {
        this.totalJobsAccepted = totalJobsAccepted;
    }

    public int getTotalDeduplicated() {
        return totalDeduplicated;
    }

    public void setTotalDeduplicated(int totalDeduplicated) {
        this.totalDeduplicated = totalDeduplicated;
    }

    public int getTotalErrors() {
        return totalErrors;
    }

    public void setTotalErrors(int totalErrors) {
        this.totalErrors = totalErrors;
    }
}
