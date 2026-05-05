package com.simfat.backend.service;

import com.simfat.backend.dto.OpenEoMeasurementIngestRequestDTO;
import com.simfat.backend.dto.OpenEoMeasurementIngestResponseDTO;

public interface OpenEoIngestService {
    OpenEoMeasurementIngestResponseDTO ingestMeasurement(OpenEoMeasurementIngestRequestDTO request);
}
