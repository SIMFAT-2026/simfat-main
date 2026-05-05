package com.simfat.backend.service;

import com.simfat.backend.dto.ForestLossRequestDTO;
import com.simfat.backend.dto.ForestLossResponseDTO;
import java.util.List;

public interface ForestLossService {

    List<ForestLossResponseDTO> getAll();

    ForestLossResponseDTO getById(String id);

    List<ForestLossResponseDTO> getByRegion(String regionId);

    List<ForestLossResponseDTO> getByYear(Integer year);

    ForestLossResponseDTO create(ForestLossRequestDTO request);

    ForestLossResponseDTO update(String id, ForestLossRequestDTO request);

    void delete(String id);
}

