package com.simfat.backend.integration.openeo;

import java.util.List;

public class OpenEoIndicatorLatestRequest {

    private String regionId;
    private AoiRequest aoi;
    private String periodStart;
    private String periodEnd;

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public AoiRequest getAoi() {
        return aoi;
    }

    public void setAoi(AoiRequest aoi) {
        this.aoi = aoi;
    }

    public String getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(String periodStart) {
        this.periodStart = periodStart;
    }

    public String getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(String periodEnd) {
        this.periodEnd = periodEnd;
    }

    public static class AoiRequest {

        private String type;
        private List<Double> coordinates;

        public AoiRequest() {
        }

        public AoiRequest(String type, List<Double> coordinates) {
            this.type = type;
            this.coordinates = coordinates;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<Double> getCoordinates() {
            return coordinates;
        }

        public void setCoordinates(List<Double> coordinates) {
            this.coordinates = coordinates;
        }
    }
}
