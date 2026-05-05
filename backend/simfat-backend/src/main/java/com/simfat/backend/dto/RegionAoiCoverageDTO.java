package com.simfat.backend.dto;

public class RegionAoiCoverageDTO {

    private String regionId;
    private String nombre;
    private String zona;
    private boolean hasAoi;
    private String source;
    private String aoiSummary;

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getZona() {
        return zona;
    }

    public void setZona(String zona) {
        this.zona = zona;
    }

    public boolean isHasAoi() {
        return hasAoi;
    }

    public void setHasAoi(boolean hasAoi) {
        this.hasAoi = hasAoi;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getAoiSummary() {
        return aoiSummary;
    }

    public void setAoiSummary(String aoiSummary) {
        this.aoiSummary = aoiSummary;
    }
}
