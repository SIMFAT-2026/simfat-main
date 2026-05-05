package com.simfat.backend.dto;

public class AlertRuleResponseDTO {

    private String id;
    private String nombre;
    private String regionId;
    private Double umbralPorcentajePerdida;
    private Integer umbralEventosCalor;
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

    public Double getUmbralPorcentajePerdida() {
        return umbralPorcentajePerdida;
    }

    public void setUmbralPorcentajePerdida(Double umbralPorcentajePerdida) {
        this.umbralPorcentajePerdida = umbralPorcentajePerdida;
    }

    public Integer getUmbralEventosCalor() {
        return umbralEventosCalor;
    }

    public void setUmbralEventosCalor(Integer umbralEventosCalor) {
        this.umbralEventosCalor = umbralEventosCalor;
    }

    public Boolean getActiva() {
        return activa;
    }

    public void setActiva(Boolean activa) {
        this.activa = activa;
    }
}

