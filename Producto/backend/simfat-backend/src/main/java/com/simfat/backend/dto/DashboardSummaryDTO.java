package com.simfat.backend.dto;

public class DashboardSummaryDTO {

    private Double totalHectareasPerdidas;
    private Integer regionesCriticas;
    private Integer totalAlertas;
    private Integer anioMayorPerdida;
    private String tendenciaGeneral;

    public Double getTotalHectareasPerdidas() {
        return totalHectareasPerdidas;
    }

    public void setTotalHectareasPerdidas(Double totalHectareasPerdidas) {
        this.totalHectareasPerdidas = totalHectareasPerdidas;
    }

    public Integer getRegionesCriticas() {
        return regionesCriticas;
    }

    public void setRegionesCriticas(Integer regionesCriticas) {
        this.regionesCriticas = regionesCriticas;
    }

    public Integer getTotalAlertas() {
        return totalAlertas;
    }

    public void setTotalAlertas(Integer totalAlertas) {
        this.totalAlertas = totalAlertas;
    }

    public Integer getAnioMayorPerdida() {
        return anioMayorPerdida;
    }

    public void setAnioMayorPerdida(Integer anioMayorPerdida) {
        this.anioMayorPerdida = anioMayorPerdida;
    }

    public String getTendenciaGeneral() {
        return tendenciaGeneral;
    }

    public void setTendenciaGeneral(String tendenciaGeneral) {
        this.tendenciaGeneral = tendenciaGeneral;
    }
}

