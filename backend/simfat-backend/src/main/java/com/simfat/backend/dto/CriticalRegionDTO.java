package com.simfat.backend.dto;

public class CriticalRegionDTO {

    private String regionId;
    private String nombreRegion;
    private Double porcentajePerdidaActual;
    private Long eventosCalorRecientes;
    private String estadoCriticidad;

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getNombreRegion() {
        return nombreRegion;
    }

    public void setNombreRegion(String nombreRegion) {
        this.nombreRegion = nombreRegion;
    }

    public Double getPorcentajePerdidaActual() {
        return porcentajePerdidaActual;
    }

    public void setPorcentajePerdidaActual(Double porcentajePerdidaActual) {
        this.porcentajePerdidaActual = porcentajePerdidaActual;
    }

    public Long getEventosCalorRecientes() {
        return eventosCalorRecientes;
    }

    public void setEventosCalorRecientes(Long eventosCalorRecientes) {
        this.eventosCalorRecientes = eventosCalorRecientes;
    }

    public String getEstadoCriticidad() {
        return estadoCriticidad;
    }

    public void setEstadoCriticidad(String estadoCriticidad) {
        this.estadoCriticidad = estadoCriticidad;
    }
}

