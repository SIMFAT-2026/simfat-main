package com.simfat.backend.service;

import com.simfat.backend.dto.HeatAlertRequestDTO;
import com.simfat.backend.dto.HeatAlertResponseDTO;
import com.simfat.backend.model.RiskLevel;
import java.time.LocalDateTime;
import java.util.List;

public interface HeatAlertService {

    List<HeatAlertResponseDTO> getAll();

    HeatAlertResponseDTO getById(String id);

    List<HeatAlertResponseDTO> getByRegion(String regionId);

    List<HeatAlertResponseDTO> getMap(String regionId, LocalDateTime from, LocalDateTime to, RiskLevel level);

    HeatAlertResponseDTO create(HeatAlertRequestDTO request);

    HeatAlertResponseDTO update(String id, HeatAlertRequestDTO request);

    void delete(String id);
}
