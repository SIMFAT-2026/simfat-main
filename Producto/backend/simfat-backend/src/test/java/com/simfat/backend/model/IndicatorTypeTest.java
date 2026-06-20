package com.simfat.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.simfat.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class IndicatorTypeTest {

    @Test
    void from_acceptsExistingAndNewClimateIndicators() {
        assertEquals(IndicatorType.NDVI, IndicatorType.from("ndvi"));
        assertEquals(IndicatorType.NDMI, IndicatorType.from("NDMI"));
        assertEquals(IndicatorType.WIND, IndicatorType.from("wind"));
        assertEquals(IndicatorType.HUMIDITY, IndicatorType.from("HUMIDITY"));
        assertEquals(IndicatorType.AIR_TEMP, IndicatorType.from("air_temp"));
        assertEquals(IndicatorType.SOIL_TEMP, IndicatorType.from("SOIL_TEMP"));
    }

    @Test
    void from_rejectsUnsupportedIndicator() {
        assertThrows(BadRequestException.class, () -> IndicatorType.from("UNKNOWN"));
    }

    @Test
    void from_rejectsBlankIndicator() {
        assertThrows(BadRequestException.class, () -> IndicatorType.from(" "));
        assertThrows(BadRequestException.class, () -> IndicatorType.from(null));
    }
}
