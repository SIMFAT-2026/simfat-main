package com.simfat.backend.integration.openeo;

import java.time.LocalDate;

public class OpenEoJobRequest {

    private String regionId;
    private String aoi;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    public OpenEoJobRequest() {
    }

    public OpenEoJobRequest(String regionId, LocalDate periodStart, LocalDate periodEnd) {
        this.regionId = regionId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public OpenEoJobRequest(String regionId, String aoi, LocalDate periodStart, LocalDate periodEnd) {
        this.regionId = regionId;
        this.aoi = aoi;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getAoi() {
        return aoi;
    }

    public void setAoi(String aoi) {
        this.aoi = aoi;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }
}
