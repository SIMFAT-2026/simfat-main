package com.simfat.backend.service.impl;

import com.simfat.backend.dto.HeatAlertRequestDTO;
import com.simfat.backend.dto.HeatAlertResponseDTO;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.RiskLevel;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.service.HeatAlertService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HeatAlertServiceImpl implements HeatAlertService {

    private final HeatAlertEventRepository heatAlertRepository;
    private final RegionRepository regionRepository;

    public HeatAlertServiceImpl(
        HeatAlertEventRepository heatAlertRepository,
        RegionRepository regionRepository
    ) {
        this.heatAlertRepository = heatAlertRepository;
        this.regionRepository = regionRepository;
    }

    @Override
    public List<HeatAlertResponseDTO> getAll() {
        return heatAlertRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public HeatAlertResponseDTO getById(String id) {
        return toResponse(findByIdInternal(id));
    }

    @Override
    public List<HeatAlertResponseDTO> getByRegion(String regionId) {
        ensureRegionExists(regionId);
        return heatAlertRepository.findByRegionId(regionId).stream().map(this::toResponse).toList();
    }

    @Override
    public List<HeatAlertResponseDTO> getMap(String regionId, LocalDateTime from, LocalDateTime to, RiskLevel level) {
        return heatAlertRepository.findAll()
            .stream()
            .filter(item -> regionId == null || regionId.isBlank() || regionId.equals(item.getRegionId()))
            .filter(item -> level == null || level == item.getNivelRiesgo())
            .filter(item -> from == null || (item.getFechaEvento() != null && !item.getFechaEvento().isBefore(from)))
            .filter(item -> to == null || (item.getFechaEvento() != null && !item.getFechaEvento().isAfter(to)))
            .map(this::toResponse)
            .toList();
    }

    @Override
    public HeatAlertResponseDTO create(HeatAlertRequestDTO request) {
        ensureRegionExists(request.getRegionId());
        HeatAlertEvent event = new HeatAlertEvent();
        applyChanges(event, request);
        return toResponse(heatAlertRepository.save(event));
    }

    @Override
    public HeatAlertResponseDTO update(String id, HeatAlertRequestDTO request) {
        ensureRegionExists(request.getRegionId());
        HeatAlertEvent existing = findByIdInternal(id);
        applyChanges(existing, request);
        return toResponse(heatAlertRepository.save(existing));
    }

    @Override
    public void delete(String id) {
        HeatAlertEvent existing = findByIdInternal(id);
        heatAlertRepository.delete(existing);
    }

    private HeatAlertEvent findByIdInternal(String id) {
        return heatAlertRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Evento de alerta no encontrado con id: " + id));
    }

    private void ensureRegionExists(String regionId) {
        regionRepository.findById(regionId)
            .orElseThrow(() -> new ResourceNotFoundException("Region no encontrada con id: " + regionId));
    }

    private void applyChanges(HeatAlertEvent event, HeatAlertRequestDTO request) {
        event.setRegionId(request.getRegionId());
        event.setFechaEvento(request.getFechaEvento() != null ? request.getFechaEvento() : LocalDateTime.now());
        event.setLatitud(request.getLatitud());
        event.setLongitud(request.getLongitud());
        event.setFuente(request.getFuente());
        event.setDescripcion(request.getDescripcion());
        event.setNivelRiesgo(request.getNivelRiesgo() != null ? request.getNivelRiesgo() : RiskLevel.BAJO);
    }

    private HeatAlertResponseDTO toResponse(HeatAlertEvent event) {
        HeatAlertResponseDTO dto = new HeatAlertResponseDTO();
        dto.setId(event.getId());
        dto.setRegionId(event.getRegionId());
        dto.setFechaEvento(event.getFechaEvento());
        dto.setNivelRiesgo(event.getNivelRiesgo());
        dto.setLatitud(event.getLatitud());
        dto.setLongitud(event.getLongitud());
        dto.setFuente(event.getFuente());
        dto.setDescripcion(event.getDescripcion());
        return dto;
    }
}
