package com.simfat.backend.service;

import com.simfat.backend.dto.RegionAoiCoverageDTO;
import com.simfat.backend.dto.RegionAoiUpdateRequestDTO;
import com.simfat.backend.dto.RegionRequestDTO;
import com.simfat.backend.dto.RegionResponseDTO;
import java.util.List;

public interface RegionService {

    List<RegionResponseDTO> getAll();

    RegionResponseDTO getById(String id);

    RegionResponseDTO create(RegionRequestDTO request);

    RegionResponseDTO update(String id, RegionRequestDTO request);

    RegionResponseDTO updateAoi(String id, RegionAoiUpdateRequestDTO request);

    List<RegionAoiCoverageDTO> getAoiCoverage();

    void delete(String id);
}
