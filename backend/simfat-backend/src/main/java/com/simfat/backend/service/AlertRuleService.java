package com.simfat.backend.service;

import com.simfat.backend.dto.AlertRuleRequestDTO;
import com.simfat.backend.dto.AlertRuleResponseDTO;
import com.simfat.backend.model.AlertRule;
import java.util.List;

public interface AlertRuleService {

    List<AlertRuleResponseDTO> getAll();

    AlertRuleResponseDTO getById(String id);

    AlertRuleResponseDTO create(AlertRuleRequestDTO request);

    AlertRuleResponseDTO update(String id, AlertRuleRequestDTO request);

    void delete(String id);

    List<AlertRule> getActiveRulesForRegion(String regionId);
}

