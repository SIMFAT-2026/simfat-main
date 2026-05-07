package com.simfat.backend.dto;

import java.time.LocalDate;
import java.util.List;

public class IndicatorMapResponseDTO {

    private String indicator;
    private LocalDate from;
    private LocalDate to;
    private int limit;
    private List<IndicatorMapPointDTO> items;

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

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public List<IndicatorMapPointDTO> getItems() {
        return items;
    }

    public void setItems(List<IndicatorMapPointDTO> items) {
        this.items = items;
    }
}
