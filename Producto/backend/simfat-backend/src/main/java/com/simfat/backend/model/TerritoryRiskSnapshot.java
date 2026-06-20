package com.simfat.backend.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "territory_risk_snapshots")
@CompoundIndexes({
    @CompoundIndex(name = "idx_risk_region_computed_desc", def = "{'regionId': 1, 'computedAt': -1}")
})
public class TerritoryRiskSnapshot {

    @Id
    private String id;

    @Indexed
    private String regionId;

    private LocalDateTime computedAt;

    private Double scoreComposite;
    private String alertLevel;
    private String qualityFlag;

    private Double componentFwi;
    private Double componentNdmi;
    private Double componentFirms;
    private Double componentLoss;
    private Double componentNdvi;
    private Double componentReports;

    private Double fwiRaw;
    private Double ndmiRaw;
    private Double ndviRaw;
    private Integer firmsCount;
    private Double firmsFrpMean;
    private Double lossRateRaw;
    private Integer reportsCount;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRegionId() { return regionId; }
    public void setRegionId(String regionId) { this.regionId = regionId; }

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

    public Double getComponentNdmi() { return componentNdmi; }
    public void setComponentNdmi(Double componentNdmi) { this.componentNdmi = componentNdmi; }

    public Double getComponentFirms() { return componentFirms; }
    public void setComponentFirms(Double componentFirms) { this.componentFirms = componentFirms; }

    public Double getComponentLoss() { return componentLoss; }
    public void setComponentLoss(Double componentLoss) { this.componentLoss = componentLoss; }

    public Double getComponentNdvi() { return componentNdvi; }
    public void setComponentNdvi(Double componentNdvi) { this.componentNdvi = componentNdvi; }

    public Double getComponentReports() { return componentReports; }
    public void setComponentReports(Double componentReports) { this.componentReports = componentReports; }

    public Double getFwiRaw() { return fwiRaw; }
    public void setFwiRaw(Double fwiRaw) { this.fwiRaw = fwiRaw; }

    public Double getNdmiRaw() { return ndmiRaw; }
    public void setNdmiRaw(Double ndmiRaw) { this.ndmiRaw = ndmiRaw; }

    public Double getNdviRaw() { return ndviRaw; }
    public void setNdviRaw(Double ndviRaw) { this.ndviRaw = ndviRaw; }

    public Integer getFirmsCount() { return firmsCount; }
    public void setFirmsCount(Integer firmsCount) { this.firmsCount = firmsCount; }

    public Double getFirmsFrpMean() { return firmsFrpMean; }
    public void setFirmsFrpMean(Double firmsFrpMean) { this.firmsFrpMean = firmsFrpMean; }

    public Double getLossRateRaw() { return lossRateRaw; }
    public void setLossRateRaw(Double lossRateRaw) { this.lossRateRaw = lossRateRaw; }

    public Integer getReportsCount() { return reportsCount; }
    public void setReportsCount(Integer reportsCount) { this.reportsCount = reportsCount; }
}
