package com.simfat.backend.dto;

public class OpenEoMeasurementIngestResponseDTO {

    private boolean synced;
    private String status;
    private String jobId;
    private boolean observationPersisted;

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public boolean isObservationPersisted() {
        return observationPersisted;
    }

    public void setObservationPersisted(boolean observationPersisted) {
        this.observationPersisted = observationPersisted;
    }
}
