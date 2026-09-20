package com.simfat.backend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Anonymous view of a moderated (VALIDADO) citizen report. No internal id, status, moderation
 * data or reporter data; coordinates are rounded to two decimals (about 1 km).
 */
public record PublicCitizenReportDTO(
    String category,
    String subCategory,
    String description,
    LocalDate createdAt,
    List<String> photos,
    Double lat,
    Double lon
) {
}
