package com.simfat.backend.dto;

public class LossTrendPointDTO {

    private Integer anio;
    private Double hectareasPerdidas;
    private Double porcentajePromedio;

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

    public Double getPorcentajePromedio() {
        return porcentajePromedio;
    }

    public void setPorcentajePromedio(Double porcentajePromedio) {
        this.porcentajePromedio = porcentajePromedio;
    }
}

