package com.simfat.backend.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "comuna_risk_snapshots")
@CompoundIndexes({
    @CompoundIndex(name = "idx_comuna_risk_computed_desc", def = "{'comunaId': 1, 'computedAt': -1}"),
    @CompoundIndex(name = "idx_region_computed_desc", def = "{'regionId': 1, 'computedAt': -1}")
})
public class ComunaRiskSnapshot {

    @Id
    private String id;

    private String comunaId;
    private String regionId;
    private String nombreComuna;
    private LocalDateTime computedAt;

    private Double scoreComposite;
    private String alertLevel;
    private String mode;         // STANDARD | ENHANCED
    private String qualityFlag;  // null | PARTIAL | COPERNICUS_UNAVAILABLE

    // Componentes modo STANDARD
    private Double componentFwi;
    private Double componentFirms;
    private Double componentReports;

    // Componentes adicionales modo ENHANCED
    private Double componentNdmi;
    private Double componentNdvi;
    private Double componentLoss;

    private String openeoObservationId;

    // Valores raw
    private Double fwiRaw;
    private Integer firmsCount;
    private Double firmsFrpMean;
    private Integer reportsCount;
    private Double ndmiRaw;
    private Double ndviRaw;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getComunaId() { return comunaId; }
    public void setComunaId(String comunaId) { this.comunaId = comunaId; }

    public String getRegionId() { return regionId; }
    public void setRegionId(String regionId) { this.regionId = regionId; }

    public String getNombreComuna() { return nombreComuna; }
    public void setNombreComuna(String nombreComuna) { this.nombreComuna = nombreComuna; }

    public LocalDateTime getComputedAt() { return computedAt; }
    public void setComputedAt(LocalDateTime computedAt) { this.computedAt = computedAt; }

    public Double getScoreComposite() { return scoreComposite; }
    public void setScoreComposite(Double scoreComposite) { this.scoreComposite = scoreComposite; }

    public String getAlertLevel() { return alertLevel; }
    public void setAlertLevel(String alertLevel) { this.alertLevel = alertLevel; }

    public String getQualityFlag() { return qualityFlag; }
    public void setQualityFlag(String qualityFlag) { this.qualityFlag = qualityFlag; }

    public Double getComponentFwi() { return componentFwi; }
    public void setComponentFwi(Double componentFwi) { this.componentFwi = componentFwi; }

    public Double getComponentFirms() { return componentFirms; }
    public void setComponentFirms(Double componentFirms) { this.componentFirms = componentFirms; }

    public Double getComponentReports() { return componentReports; }
    public void setComponentReports(Double componentReports) { this.componentReports = componentReports; }

    public Double getComponentNdmi() { return componentNdmi; }
    public void setComponentNdmi(Double componentNdmi) { this.componentNdmi = componentNdmi; }

    public Double getComponentNdvi() { return componentNdvi; }
    public void setComponentNdvi(Double componentNdvi) { this.componentNdvi = componentNdvi; }

    public Double getComponentLoss() { return componentLoss; }
    public void setComponentLoss(Double componentLoss) { this.componentLoss = componentLoss; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getOpeneoObservationId() { return openeoObservationId; }
    public void setOpeneoObservationId(String openeoObservationId) { this.openeoObservationId = openeoObservationId; }

    public Double getFwiRaw() { return fwiRaw; }
    public void setFwiRaw(Double fwiRaw) { this.fwiRaw = fwiRaw; }

    public Integer getFirmsCount() { return firmsCount; }
    public void setFirmsCount(Integer firmsCount) { this.firmsCount = firmsCount; }

    public Double getFirmsFrpMean() { return firmsFrpMean; }
    public void setFirmsFrpMean(Double firmsFrpMean) { this.firmsFrpMean = firmsFrpMean; }

    public Integer getReportsCount() { return reportsCount; }
    public void setReportsCount(Integer reportsCount) { this.reportsCount = reportsCount; }

    public Double getNdmiRaw() { return ndmiRaw; }
    public void setNdmiRaw(Double ndmiRaw) { this.ndmiRaw = ndmiRaw; }

    public Double getNdviRaw() { return ndviRaw; }
    public void setNdviRaw(Double ndviRaw) { this.ndviRaw = ndviRaw; }
}
