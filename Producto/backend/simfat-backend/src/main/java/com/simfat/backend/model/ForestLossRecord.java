package com.simfat.backend.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "forest_loss_records")
public class ForestLossRecord {

    @Id
    private String id;

    @NotBlank(message = "El regionId es obligatorio")
    private String regionId;

    @NotNull(message = "El anio es obligatorio")
    @Min(value = 1900, message = "El anio debe ser mayor o igual a 1900")
    private Integer anio;

    @NotNull(message = "Las hectareas perdidas son obligatorias")
    @DecimalMin(value = "0.0", inclusive = true, message = "Las hectareas perdidas no pueden ser negativas")
    private Double hectareasPerdidas;

    @DecimalMin(value = "0.0", inclusive = true, message = "El porcentaje de perdida no puede ser negativo")
    private Double porcentajePerdida;

    @NotBlank(message = "La fuente es obligatoria")
    @Size(max = 120, message = "La fuente no puede exceder 120 caracteres")
    private String fuente;

    @NotNull(message = "La fecha de registro es obligatoria")
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

