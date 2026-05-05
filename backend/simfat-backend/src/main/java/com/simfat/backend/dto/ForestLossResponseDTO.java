package com.simfat.backend.dto;

import java.time.LocalDateTime;

public class ForestLossResponseDTO {

    private String id;
    private String regionId;
    private Integer anio;
    private Double hectareasPerdidas;
    private Double porcentajePerdida;
    private String fuente;
    private LocalDateTime fechaRegistro;

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

    public Integer getAnio() {
        return anio;
    }

    public void setAnio(Integer anio) {
        this.anio = anio;
    }

    public Double getHectareasPerdidas() {
        return hectareasPerdidas;
    }

    public void setHectareasPerdidas(Double hectareasPerdidas) {
        this.hectareasPerdidas = hectareasPerdidas;
    }

    public Double getPorcentajePerdida() {
        return porcentajePerdida;
    }

    public void setPorcentajePerdida(Double porcentajePerdida) {
        this.porcentajePerdida = porcentajePerdida;
    }

    public String getFuente() {
        return fuente;
    }

    public void setFuente(String fuente) {
        this.fuente = fuente;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }
}

