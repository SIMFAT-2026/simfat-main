package com.simfat.backend.service.impl;

import com.simfat.backend.dto.AlertRuleRequestDTO;
import com.simfat.backend.dto.AlertRuleResponseDTO;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.model.AlertRule;
import com.simfat.backend.repository.AlertRuleRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.service.AlertRuleService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AlertRuleServiceImpl implements AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;
    private final RegionRepository regionRepository;

    public AlertRuleServiceImpl(AlertRuleRepository alertRuleRepository, RegionRepository regionRepository) {
        this.alertRuleRepository = alertRuleRepository;
        this.regionRepository = regionRepository;
    }

    @Override
    public List<AlertRuleResponseDTO> getAll() {
        return alertRuleRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public AlertRuleResponseDTO getById(String id) {
        return toResponse(findByIdInternal(id));
    }

    @Override
    public AlertRuleResponseDTO create(AlertRuleRequestDTO request) {
        validateOptionalRegion(request.getRegionId());
        AlertRule rule = new AlertRule();
        applyChanges(rule, request);
        return toResponse(alertRuleRepository.save(rule));
    }

    @Override
    public AlertRuleResponseDTO update(String id, AlertRuleRequestDTO request) {
        validateOptionalRegion(request.getRegionId());
        AlertRule existing = findByIdInternal(id);
        applyChanges(existing, request);
        return toResponse(alertRuleRepository.save(existing));
    }

    @Override
    public void delete(String id) {
        AlertRule existing = findByIdInternal(id);
        alertRuleRepository.delete(existing);
    }

    @Override
    public List<AlertRule> getActiveRulesForRegion(String regionId) {
        return alertRuleRepository.findByActivaTrue().stream()
            .filter(rule -> rule.getRegionId() == null || rule.getRegionId().isBlank() || rule.getRegionId().equals(regionId))
            .toList();
    }

    private AlertRule findByIdInternal(String id) {
        return alertRuleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Regla de alerta no encontrada con id: " + id));
    }

    private void validateOptionalRegion(String regionId) {
        if (regionId == null || regionId.isBlank()) {
            return;
        }
        regionRepository.findById(regionId)
            .orElseThrow(() -> new ResourceNotFoundException("Region no encontrada con id: " + regionId));
    }

    private void applyChanges(AlertRule rule, AlertRuleRequestDTO request) {
        rule.setNombre(request.getNombre());
        rule.setRegionId(request.getRegionId());
        rule.setUmbralPorcentajePerdida(request.getUmbralPorcentajePerdida());
        rule.setUmbralEventosCalor(request.getUmbralEventosCalor());
        rule.setActiva(request.getActiva());
    }

    private AlertRuleResponseDTO toResponse(AlertRule rule) {
        AlertRuleResponseDTO dto = new AlertRuleResponseDTO();
        dto.setId(rule.getId());
        dto.setNombre(rule.getNombre());
        dto.setRegionId(rule.getRegionId());
        dto.setUmbralPorcentajePerdida(rule.getUmbralPorcentajePerdida());
        dto.setUmbralEventosCalor(rule.getUmbralEventosCalor());
        dto.setActiva(rule.getActiva());
        return dto;
    }
}

