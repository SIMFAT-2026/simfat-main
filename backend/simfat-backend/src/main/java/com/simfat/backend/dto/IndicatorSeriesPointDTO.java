package com.simfat.backend.dto;

import java.time.LocalDateTime;

public class IndicatorSeriesPointDTO {

    private LocalDateTime ts;
    private Double value;

    public LocalDateTime getTs() {
        return ts;
    }

    public void setTs(LocalDateTime ts) {
        this.ts = ts;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    // Backward compatibility with previous payload field name.
    public LocalDateTime getObservedAt() {
        return ts;
    }

    public void setObservedAt(LocalDateTime observedAt) {
        this.ts = observedAt;
    }
}
