package com.simfat.backend.model;

import com.simfat.backend.exception.BadRequestException;

public enum IndicatorType {
    NDVI,
    NDMI,
    WIND,
    HUMIDITY,
    AIR_TEMP,
    SOIL_TEMP;

    public static IndicatorType from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new BadRequestException("El indicador es obligatorio");
        }
        try {
            return IndicatorType.valueOf(rawValue.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Indicador invalido. Valores permitidos: NDVI, NDMI, WIND, HUMIDITY, AIR_TEMP, SOIL_TEMP");
        }
    }
}
