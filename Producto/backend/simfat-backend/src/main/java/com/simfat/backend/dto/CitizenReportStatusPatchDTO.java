package com.simfat.backend.dto;

import com.simfat.backend.model.CitizenReportStatus;
import jakarta.validation.constraints.NotNull;

public class CitizenReportStatusPatchDTO {

    @NotNull(message = "El estado es obligatorio")
    private CitizenReportStatus status;

    public CitizenReportStatus getStatus() {
        return status;
    }

    public void setStatus(CitizenReportStatus status) {
        this.status = status;
    }
}
