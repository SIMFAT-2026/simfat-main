package com.simfat.backend.dto;

import java.util.List;

public class RegionResponseDTO {

    private String id;
    private String nombre;
    private String codigo;
    private String zona;
    private Double hectareasBosqueReferencia;
    private List<Double> aoiBbox;

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

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getZona() {
        return zona;
    }

    public void setZona(String zona) {
        this.zona = zona;
    }

    public Double getHectareasBosqueReferencia() {
        return hectareasBosqueReferencia;
    }

    public void setHectareasBosqueReferencia(Double hectareasBosqueReferencia) {
        this.hectareasBosqueReferencia = hectareasBosqueReferencia;
    }

    public List<Double> getAoiBbox() {
        return aoiBbox;
    }

    public void setAoiBbox(List<Double> aoiBbox) {
        this.aoiBbox = aoiBbox;
    }
}
