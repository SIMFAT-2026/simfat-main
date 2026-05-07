package com.simfat.backend.dto;

import com.simfat.backend.model.RiskLevel;
import java.time.LocalDateTime;

public class HeatAlertResponseDTO {

    private String id;
    private String regionId;
    private LocalDateTime fechaEvento;
    private RiskLevel nivelRiesgo;
    private Double latitud;
    private Double longitud;
    private String fuente;
    private String descripcion;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public LocalDateTime getFechaEvento() {
        return fechaEvento;
    }

    public void setFechaEvento(LocalDateTime fechaEvento) {
        this.fechaEvento = fechaEvento;
    }

    public RiskLevel getNivelRiesgo() {
        return nivelRiesgo;
    }

    public void setNivelRiesgo(RiskLevel nivelRiesgo) {
        this.nivelRiesgo = nivelRiesgo;
    }

    public Double getLatitud() {
        return latitud;
    }

    public void setLatitud(Double latitud) {
        this.latitud = latitud;
    }

    public Double getLongitud() {
        return longitud;
    }

    public void setLongitud(Double longitud) {
        this.longitud = longitud;
    }

    public String getFuente() {
        return fuente;
    }

    public void setFuente(String fuente) {
        this.fuente = fuente;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
}

