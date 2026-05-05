package com.simfat.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public class OpenEoMeasurementIngestRequestDTO {

    @NotBlank(message = "regionId es obligatorio")
    private String regionId;

    @NotBlank(message = "indicatorType es obligatorio")
    private String indicatorType;

    @NotNull(message = "periodStart es obligatorio")
    private LocalDate periodStart;

    @NotNull(message = "periodEnd es obligatorio")
    private LocalDate periodEnd;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSS]XXX")
    private OffsetDateTime fetchedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSS]XXX")
    private OffsetDateTime measuredAt;

    private String collectionId;
    private Double value;
    private String source;
    private Boolean cached;
    private String unit;
    private String quality;

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getIndicatorType() {
        return indicatorType;
    }

    public void setIndicatorType(String indicatorType) {
        this.indicatorType = indicatorType;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public OffsetDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(OffsetDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public OffsetDateTime getMeasuredAt() {
        return measuredAt;
    }

    public void setMeasuredAt(OffsetDateTime measuredAt) {
        this.measuredAt = measuredAt;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Boolean getCached() {
        return cached;
    }

    public void setCached(Boolean cached) {
        this.cached = cached;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }
}
