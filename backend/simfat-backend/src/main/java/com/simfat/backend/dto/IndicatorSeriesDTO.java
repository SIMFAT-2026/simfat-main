package com.simfat.backend.dto;

import java.time.LocalDate;
import java.util.List;

public class IndicatorSeriesDTO {

    private String regionId;
    private String indicator;
    private LocalDate from;
    private LocalDate to;
    private String granularity;
    private List<IndicatorSeriesPointDTO> points;

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getIndicator() {
        return indicator;
    }

    public void setIndicator(String indicator) {
        this.indicator = indicator;
    }

    public LocalDate getFrom() {
        return from;
    }

    public void setFrom(LocalDate from) {
        this.from = from;
    }

    public LocalDate getTo() {
        return to;
    }

    public void setTo(LocalDate to) {
        this.to = to;
    }

    public String getGranularity() {
        return granularity;
    }

    public void setGranularity(String granularity) {
        this.granularity = granularity;
    }

    public List<IndicatorSeriesPointDTO> getPoints() {
        return points;
    }

    public void setPoints(List<IndicatorSeriesPointDTO> points) {
        this.points = points;
    }
}
