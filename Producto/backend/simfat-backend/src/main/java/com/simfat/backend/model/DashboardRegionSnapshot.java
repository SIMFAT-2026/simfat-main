package com.simfat.backend.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "dashboard_region_snapshots")
@CompoundIndexes({
    @CompoundIndex(name = "idx_computedAt_desc", def = "{'computedAt': -1}")
})
public class DashboardRegionSnapshot {

    @Id
    private String id;

    @Indexed(unique = true)
    private String regionId;

    private Double latestNdvi;
    private Double latestNdmi;
    private Double ndviTrend30d;
    private Double ndmiTrend30d;
    private Long heatAlerts7d;
    private Double forestLossCurrentPct;
    private String criticality;
    private LocalDateTime computedAt;
    private Long dataFreshnessSeconds;

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

    public Double getLatestNdvi() {
        return latestNdvi;
    }

    public void setLatestNdvi(Double latestNdvi) {
        this.latestNdvi = latestNdvi;
    }

    public Double getLatestNdmi() {
        return latestNdmi;
    }

    public void setLatestNdmi(Double latestNdmi) {
        this.latestNdmi = latestNdmi;
    }

    public Double getNdviTrend30d() {
        return ndviTrend30d;
    }

    public void setNdviTrend30d(Double ndviTrend30d) {
        this.ndviTrend30d = ndviTrend30d;
    }

    public Double getNdmiTrend30d() {
        return ndmiTrend30d;
    }

    public void setNdmiTrend30d(Double ndmiTrend30d) {
        this.ndmiTrend30d = ndmiTrend30d;
    }

    public Long getHeatAlerts7d() {
        return heatAlerts7d;
    }

    public void setHeatAlerts7d(Long heatAlerts7d) {
        this.heatAlerts7d = heatAlerts7d;
    }

    public Double getForestLossCurrentPct() {
        return forestLossCurrentPct;
    }

    public void setForestLossCurrentPct(Double forestLossCurrentPct) {
        this.forestLossCurrentPct = forestLossCurrentPct;
    }

    public String getCriticality() {
        return criticality;
    }

    public void setCriticality(String criticality) {
        this.criticality = criticality;
    }

    public LocalDateTime getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(LocalDateTime computedAt) {
        this.computedAt = computedAt;
    }

    public Long getDataFreshnessSeconds() {
        return dataFreshnessSeconds;
    }

    public void setDataFreshnessSeconds(Long dataFreshnessSeconds) {
        this.dataFreshnessSeconds = dataFreshnessSeconds;
    }
}
