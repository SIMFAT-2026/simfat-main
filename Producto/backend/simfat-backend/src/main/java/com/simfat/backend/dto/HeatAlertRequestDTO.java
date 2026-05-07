package com.simfat.backend.dto;

import com.simfat.backend.model.RiskLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public class HeatAlertRequestDTO {

    @NotBlank(message = "El regionId es obligatorio")
    private String regionId;

    private LocalDateTime fechaEvento;

    private RiskLevel nivelRiesgo;

    @NotNull(message = "La latitud es obligatoria")
    @DecimalMin(value = "-90.0", message = "La latitud no puede ser menor a -90")
    @DecimalMax(value = "90.0", message = "La latitud no puede ser mayor a 90")
    private Double latitud;

    @NotNull(message = "La longitud es obligatoria")
    @DecimalMin(value = "-180.0", message = "La longitud no puede ser menor a -180")
    @DecimalMax(value = "180.0", message = "La longitud no puede ser mayor a 180")
    private Double longitud;

    @NotBlank(message = "La fuente es obligatoria")
    @Size(max = 120, message = "La fuente no puede exceder 120 caracteres")
    private String fuente;

    @Size(max = 500, message = "La descripcion no puede exceder 500 caracteres")
    private String descripcion;

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

