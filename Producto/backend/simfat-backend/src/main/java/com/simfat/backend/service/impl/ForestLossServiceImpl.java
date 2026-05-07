package com.simfat.backend.service.impl;

import com.simfat.backend.dto.ForestLossRequestDTO;
import com.simfat.backend.dto.ForestLossResponseDTO;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.model.ForestLossRecord;
import com.simfat.backend.model.Region;
import com.simfat.backend.repository.ForestLossRecordRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.service.ForestLossService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ForestLossServiceImpl implements ForestLossService {

    private final ForestLossRecordRepository forestLossRepository;
    private final RegionRepository regionRepository;

    @Value("${app.forest.default-reference-hectares:100000.0}")
    private Double defaultReferenceHectares;

    public ForestLossServiceImpl(ForestLossRecordRepository forestLossRepository, RegionRepository regionRepository) {
        this.forestLossRepository = forestLossRepository;
        this.regionRepository = regionRepository;
    }

    @Override
    public List<ForestLossResponseDTO> getAll() {
        return forestLossRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public ForestLossResponseDTO getById(String id) {
        return toResponse(findRecordById(id));
    }

    @Override
    public List<ForestLossResponseDTO> getByRegion(String regionId) {
        ensureRegionExists(regionId);
        return forestLossRepository.findByRegionId(regionId).stream().map(this::toResponse).toList();
    }

    @Override
    public List<ForestLossResponseDTO> getByYear(Integer year) {
        return forestLossRepository.findByAnio(year).stream().map(this::toResponse).toList();
    }

    @Override
    public ForestLossResponseDTO create(ForestLossRequestDTO request) {
        Region region = ensureRegionExists(request.getRegionId());
        ForestLossRecord record = new ForestLossRecord();
        applyChanges(record, request, region);
        return toResponse(forestLossRepository.save(record));
    }

    @Override
    public ForestLossResponseDTO update(String id, ForestLossRequestDTO request) {
        ForestLossRecord existing = findRecordById(id);
        Region region = ensureRegionExists(request.getRegionId());
        applyChanges(existing, request, region);
        return toResponse(forestLossRepository.save(existing));
    }

    @Override
    public void delete(String id) {
        ForestLossRecord existing = findRecordById(id);
        forestLossRepository.delete(existing);
    }

    private ForestLossRecord findRecordById(String id) {
        return forestLossRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Registro de perdida forestal no encontrado con id: " + id));
    }

    private Region ensureRegionExists(String regionId) {
        return regionRepository.findById(regionId)
            .orElseThrow(() -> new ResourceNotFoundException("Region no encontrada con id: " + regionId));
    }

    private void applyChanges(ForestLossRecord record, ForestLossRequestDTO request, Region region) {
        record.setRegionId(request.getRegionId());
        record.setAnio(request.getAnio());
        record.setHectareasPerdidas(request.getHectareasPerdidas());
        record.setFuente(request.getFuente());
        record.setFechaRegistro(request.getFechaRegistro() != null ? request.getFechaRegistro() : LocalDateTime.now());

        if (request.getPorcentajePerdida() != null) {
            record.setPorcentajePerdida(request.getPorcentajePerdida());
            return;
        }

        Double referenceArea = region.getHectareasBosqueReferencia() != null
            ? region.getHectareasBosqueReferencia()
            : defaultReferenceHectares;
        double percentage = referenceArea > 0
            ? (request.getHectareasPerdidas() / referenceArea) * 100
            : 0.0;
        record.setPorcentajePerdida(roundTwoDecimals(percentage));
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private ForestLossResponseDTO toResponse(ForestLossRecord record) {
        ForestLossResponseDTO dto = new ForestLossResponseDTO();
        dto.setId(record.getId());
        dto.setRegionId(record.getRegionId());
        dto.setAnio(record.getAnio());
        dto.setHectareasPerdidas(record.getHectareasPerdidas());
        dto.setPorcentajePerdida(record.getPorcentajePerdida());
        dto.setFuente(record.getFuente());
        dto.setFechaRegistro(record.getFechaRegistro());
        return dto;
    }
}

