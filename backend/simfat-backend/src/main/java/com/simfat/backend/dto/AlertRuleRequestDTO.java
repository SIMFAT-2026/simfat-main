package com.simfat.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AlertRuleRequestDTO {

    @NotBlank(message = "El nombre de la regla es obligatorio")
    @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
    private String nombre;

    private String regionId;

    @NotNull(message = "El umbral de porcentaje de perdida es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El umbral de perdida no puede ser negativo")
    private Double umbralPorcentajePerdida;

    @NotNull(message = "El umbral de eventos de calor es obligatorio")
    @Min(value = 0, message = "El umbral de eventos no puede ser negativo")
    private Integer umbralEventosCalor;

    @NotNull(message = "El estado de la regla es obligatorio")
    private Boolean activa;

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

