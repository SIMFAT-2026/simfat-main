package com.simfat.backend.dto;

public class AlertRuleResponseDTO {

    private String id;
    private String nombre;
    private String regionId;
    private Double umbralFwi;
    private Double umbralNdmi;
    private Double umbralNdvi;
    private Integer umbralFirmsCount;
    private Integer umbralReportesCiudadanos;
    private Boolean activa;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public Double getUmbralFwi() {
        return umbralFwi;
    }

    public void setUmbralFwi(Double umbralFwi) {
        this.umbralFwi = umbralFwi;
    }

    public Double getUmbralNdmi() {
        return umbralNdmi;
    }

    public void setUmbralNdmi(Double umbralNdmi) {
        this.umbralNdmi = umbralNdmi;
    }

    public Double getUmbralNdvi() {
        return umbralNdvi;
    }

    public void setUmbralNdvi(Double umbralNdvi) {
        this.umbralNdvi = umbralNdvi;
    }

    public Integer getUmbralFirmsCount() {
        return umbralFirmsCount;
    }

    public void setUmbralFirmsCount(Integer umbralFirmsCount) {
        this.umbralFirmsCount = umbralFirmsCount;
    }

    public Integer getUmbralReportesCiudadanos() {
        return umbralReportesCiudadanos;
    }

    public void setUmbralReportesCiudadanos(Integer umbralReportesCiudadanos) {
        this.umbralReportesCiudadanos = umbralReportesCiudadanos;
    }

    public Boolean getActiva() {
        return activa;
    }

    public void setActiva(Boolean activa) {
        this.activa = activa;
    }
}
