package com.simfat.backend.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "openeo_indicator_observations")
@CompoundIndexes({
    @CompoundIndex(name = "idx_region_indicator_observedAt_desc", def = "{'regionId': 1, 'indicator': 1, 'observedAt': -1}"),
    @CompoundIndex(name = "uk_region_indicator_observedAt", def = "{'regionId': 1, 'indicator': 1, 'observedAt': 1}", unique = true)
})
public class OpenEoIndicatorObservation {

    @Id
    private String id;

    private String regionId;
    private IndicatorType indicator;
    private LocalDateTime observedAt;
    private Double value;
    private String unit;
    private String aoi;
    private String quality;
    private String source;
    private LocalDateTime ingestedAt;

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

    public IndicatorType getIndicator() {
        return indicator;
    }

    public void setIndicator(IndicatorType indicator) {
        this.indicator = indicator;
    }

    public LocalDateTime getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(LocalDateTime observedAt) {
        this.observedAt = observedAt;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getAoi() {
        return aoi;
    }

    public void setAoi(String aoi) {
        this.aoi = aoi;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(LocalDateTime ingestedAt) {
        this.ingestedAt = ingestedAt;
    }
}
