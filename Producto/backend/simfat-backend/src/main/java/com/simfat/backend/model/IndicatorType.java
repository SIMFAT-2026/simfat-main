package com.simfat.backend.model;

import com.simfat.backend.exception.BadRequestException;

public enum IndicatorType {
    NDVI,
    NDMI;

    public static IndicatorType from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new BadRequestException("El indicador es obligatorio");
        }
        try {
            return IndicatorType.valueOf(rawValue.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Indicador invalido. Valores permitidos: NDVI, NDMI");
        }
    }
}
