package com.simfat.backend.dto;

import com.simfat.backend.model.RiskLevel;
import java.time.LocalDate;

/**
 * Minimized anonymous view of a heat alert. Deliberately has no id, source or description, and
 * carries coordinates rounded to two decimals (about 1 km).
 */
public record PublicHeatAlertDTO(
    String comuna,
    boolean comunaLevel,
    String regionId,
    LocalDate fecha,
    RiskLevel nivelRiesgo,
    Double lat,
    Double lon
) {
}
