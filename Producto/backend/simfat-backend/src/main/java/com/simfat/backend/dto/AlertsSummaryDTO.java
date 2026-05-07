package com.simfat.backend.dto;

public class AlertsSummaryDTO {

    private Long total;
    private Long bajo;
    private Long medio;
    private Long alto;
    private Long critico;

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Long getBajo() {
        return bajo;
    }

    public void setBajo(Long bajo) {
        this.bajo = bajo;
    }

    public Long getMedio() {
        return medio;
    }

    public void setMedio(Long medio) {
        this.medio = medio;
    }

    public Long getAlto() {
        return alto;
    }

    public void setAlto(Long alto) {
        this.alto = alto;
    }

    public Long getCritico() {
        return critico;
    }

    public void setCritico(Long critico) {
        this.critico = critico;
    }
}

