package com.simfat.backend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Regla de alerta configurable sobre las variables reales del score de riesgo WLC
 * (FWI, NDMI, NDVI, conteo FIRMS y reportes ciudadanos) que alimentan {@link ComunaRiskSnapshot}.
 *
 * <p>Todos los umbrales son opcionales (nullable). La evaluacion es OR: si cualquiera de los
 * umbrales configurados es superado, la regla se considera activada.
 *
 * <p>Direccion de comparacion:
 * <ul>
 *   <li>FWI, conteo FIRMS y reportes ciudadanos: dispara si el valor del snapshot es
 *       mayor o igual al umbral (mas alto = mas riesgo).</li>
 *   <li>NDMI y NDVI: dispara si el valor del snapshot es menor o igual al umbral
 *       (mas seco / menos vegetacion = mas riesgo).</li>
 * </ul>
 */
@Document(collection = "alert_rules")
public class AlertRule {

    @Id
    private String id;

    @NotBlank(message = "El nombre de la regla es obligatorio")
    @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
    private String nombre;

    private String regionId;

    /** Dispara si snapshot.fwiRaw >= umbralFwi. */
    private Double umbralFwi;

    /** Dispara si snapshot.ndmiRaw <= umbralNdmi (mas seco = riesgo). */
    private Double umbralNdmi;

    /** Dispara si snapshot.ndviRaw <= umbralNdvi (menos vegetacion = riesgo). */
    private Double umbralNdvi;

    /** Dispara si snapshot.firmsCount >= umbralFirmsCount. */
    private Integer umbralFirmsCount;

    /** Dispara si snapshot.reportsCount >= umbralReportesCiudadanos. */
    private Integer umbralReportesCiudadanos;

    @NotNull(message = "El estado de la regla es obligatorio")
    private Boolean activa;

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

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public Double getUmbralFwi() {
        return umbralFwi;
    }

    public void setUmbralFwi(Double umbralFwi) {
        this.umbralFwi = umbralFwi;
    }

    public Double getUmbralNdmi() {
        return umbralNdmi;
    }

    public void setUmbralNdmi(Double umbralNdmi) {
        this.umbralNdmi = umbralNdmi;
    }

    public Double getUmbralNdvi() {
        return umbralNdvi;
    }

    public void setUmbralNdvi(Double umbralNdvi) {
        this.umbralNdvi = umbralNdvi;
    }

    public Integer getUmbralFirmsCount() {
        return umbralFirmsCount;
    }

    public void setUmbralFirmsCount(Integer umbralFirmsCount) {
        this.umbralFirmsCount = umbralFirmsCount;
    }

    public Integer getUmbralReportesCiudadanos() {
        return umbralReportesCiudadanos;
    }

    public void setUmbralReportesCiudadanos(Integer umbralReportesCiudadanos) {
        this.umbralReportesCiudadanos = umbralReportesCiudadanos;
    }

    public Boolean getActiva() {
        return activa;
    }

    public void setActiva(Boolean activa) {
        this.activa = activa;
    }
}
