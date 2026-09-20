package com.simfat.backend.service.impl;

import com.simfat.backend.dto.HeatAlertRequestDTO;
import com.simfat.backend.dto.HeatAlertResponseDTO;
import com.simfat.backend.dto.PublicHeatAlertDTO;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.Region;
import com.simfat.backend.model.RiskLevel;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.service.HeatAlertService;
import com.simfat.backend.web.CoordinateRounding;
import com.simfat.backend.web.PublicQueryWindow;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class HeatAlertServiceImpl implements HeatAlertService {

    private final HeatAlertEventRepository heatAlertRepository;
    private final RegionRepository regionRepository;
    private final ComunaInfoRepository comunaInfoRepository;

    public HeatAlertServiceImpl(
        HeatAlertEventRepository heatAlertRepository,
        RegionRepository regionRepository,
        ComunaInfoRepository comunaInfoRepository
    ) {
        this.heatAlertRepository = heatAlertRepository;
        this.regionRepository = regionRepository;
        this.comunaInfoRepository = comunaInfoRepository;
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
    public List<PublicHeatAlertDTO> getPublicMap(String regionId, LocalDateTime from, LocalDateTime endExclusive) {
        List<HeatAlertEvent> events = heatAlertRepository.findByRegionIdInWindowNewestFirst(
                regionId, from, endExclusive, PageRequest.of(0, PublicQueryWindow.MAX_PUBLIC_ALERTS))
            .stream()
            .limit(PublicQueryWindow.MAX_PUBLIC_ALERTS)
            .toList();
        if (events.isEmpty()) {
            return List.of();
        }

        // One batched, projected comuna lookup per request (never per event, never the polygons).
        Map<String, String> comunaNames = new HashMap<>();
        for (ComunaInfo comuna : comunaInfoRepository.findNamesByRegionId(regionId)) {
            comunaNames.put(comuna.getId(), comuna.getNombre());
        }
        String regionLabel = regionRepository.findById(regionId).map(Region::getNombre).orElse(regionId);

        return events.stream()
            .map(event -> {
                String comunaName = event.getComunaId() == null ? null : comunaNames.get(event.getComunaId());
                return new PublicHeatAlertDTO(
                    comunaName != null ? comunaName : regionLabel,
                    comunaName != null,
                    event.getRegionId(),
                    event.getFechaEvento() == null ? null : event.getFechaEvento().toLocalDate(),
                    event.getNivelRiesgo(),
                    CoordinateRounding.round(event.getLatitud()),
                    CoordinateRounding.round(event.getLongitud())
                );
            })
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
