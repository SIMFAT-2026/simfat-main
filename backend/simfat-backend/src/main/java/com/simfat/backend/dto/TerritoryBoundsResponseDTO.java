package com.simfat.backend.dto;

import java.util.List;

public class TerritoryBoundsResponseDTO {

    private String regionId;
    private List<List<Double>> bounds;
    private List<Double> center;
    private Integer zoom;

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public List<List<Double>> getBounds() {
        return bounds;
    }

    public void setBounds(List<List<Double>> bounds) {
        this.bounds = bounds;
    }

    public List<Double> getCenter() {
        return center;
    }

    public void setCenter(List<Double> center) {
        this.center = center;
    }

    public Integer getZoom() {
        return zoom;
    }

    public void setZoom(Integer zoom) {
        this.zoom = zoom;
    }
}
