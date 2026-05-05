package com.simfat.backend.service.impl;

import com.simfat.backend.config.OpenEoProperties;
import com.simfat.backend.dto.RegionAoiCoverageDTO;
import com.simfat.backend.dto.RegionAoiUpdateRequestDTO;
import com.simfat.backend.dto.RegionRequestDTO;
import com.simfat.backend.dto.RegionResponseDTO;
import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.model.Region;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.service.RegionService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RegionServiceImpl implements RegionService {

    private final RegionRepository regionRepository;
    private final OpenEoProperties openEoProperties;

    public RegionServiceImpl(RegionRepository regionRepository, OpenEoProperties openEoProperties) {
        this.regionRepository = regionRepository;
        this.openEoProperties = openEoProperties;
    }

    @Override
    public List<RegionResponseDTO> getAll() {
        return regionRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public RegionResponseDTO getById(String id) {
        Region region = findRegionById(id);
        return toResponse(region);
    }

    @Override
    public RegionResponseDTO create(RegionRequestDTO request) {
        regionRepository.findByCodigo(request.getCodigo())
            .ifPresent(existing -> {
                throw new BadRequestException("Ya existe una region con el codigo: " + request.getCodigo());
            });

        Region region = new Region();
        applyChanges(region, request);
        return toResponse(regionRepository.save(region));
    }

    @Override
    public RegionResponseDTO update(String id, RegionRequestDTO request) {
        Region existing = findRegionById(id);
        regionRepository.findByCodigo(request.getCodigo())
            .filter(region -> !region.getId().equals(id))
            .ifPresent(region -> {
                throw new BadRequestException("Ya existe otra region con el codigo: " + request.getCodigo());
            });

        applyChanges(existing, request);
        return toResponse(regionRepository.save(existing));
    }

    @Override
    public RegionResponseDTO updateAoi(String id, RegionAoiUpdateRequestDTO request) {
        Region existing = findRegionById(id);
        List<Double> bbox = request != null ? request.getAoiBbox() : null;
        existing.setAoiBbox(validateAndNormalizeAoiBbox(bbox, "aoiBbox"));
        return toResponse(regionRepository.save(existing));
    }

    @Override
    public List<RegionAoiCoverageDTO> getAoiCoverage() {
        String rawAoiMap = openEoProperties.getAoi() != null ? openEoProperties.getAoi().getBboxMap() : "";
        Map<String, List<Double>> envAoiByCode = parseAoiMapFromEnv(rawAoiMap);
        return regionRepository.findAll().stream().map(region -> {
            List<Double> dbAoi = region.getAoiBbox();
            List<Double> envAoi = envAoiByCode.get(region.getCodigo() != null ? region.getCodigo().trim().toUpperCase() : "");

            RegionAoiCoverageDTO dto = new RegionAoiCoverageDTO();
            dto.setRegionId(region.getId());
            dto.setNombre(region.getNombre());
            dto.setZona(region.getZona());

            if (isValidBbox(dbAoi)) {
                dto.setHasAoi(true);
                dto.setSource("aoi_db");
                dto.setAoiSummary(formatBbox(dbAoi));
            } else if (isValidBbox(envAoi)) {
                dto.setHasAoi(true);
                dto.setSource("aoi_env");
                dto.setAoiSummary(formatBbox(envAoi));
            } else {
                dto.setHasAoi(false);
                dto.setSource("missing");
                dto.setAoiSummary(null);
            }
            return dto;
        }).toList();
    }

    @Override
    public void delete(String id) {
        Region region = findRegionById(id);
        regionRepository.delete(region);
    }

    private Region findRegionById(String id) {
        return regionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Region no encontrada con id: " + id));
    }

    private void applyChanges(Region region, RegionRequestDTO request) {
        region.setNombre(request.getNombre());
        region.setCodigo(request.getCodigo());
        region.setZona(request.getZona());
        region.setHectareasBosqueReferencia(request.getHectareasBosqueReferencia());
        region.setAoiBbox(validateAndNormalizeAoiBbox(request.getAoiBbox(), "aoiBbox"));
    }

    private RegionResponseDTO toResponse(Region region) {
        RegionResponseDTO dto = new RegionResponseDTO();
        dto.setId(region.getId());
        dto.setNombre(region.getNombre());
        dto.setCodigo(region.getCodigo());
        dto.setZona(region.getZona());
        dto.setHectareasBosqueReferencia(region.getHectareasBosqueReferencia());
        dto.setAoiBbox(region.getAoiBbox());
        return dto;
    }

    private List<Double> validateAndNormalizeAoiBbox(List<Double> bbox, String fieldName) {
        if (bbox == null) {
            return null;
        }
        if (bbox.size() != 4) {
            throw new BadRequestException(fieldName + " debe tener exactamente 4 coordenadas: [west,south,east,north]");
        }

        double west = requireFinite(bbox.get(0), fieldName + "[0]");
        double south = requireFinite(bbox.get(1), fieldName + "[1]");
        double east = requireFinite(bbox.get(2), fieldName + "[2]");
        double north = requireFinite(bbox.get(3), fieldName + "[3]");

        if (west < -180 || west > 180 || east < -180 || east > 180) {
            throw new BadRequestException(fieldName + " longitud invalida. Rango permitido: -180..180");
        }
        if (south < -90 || south > 90 || north < -90 || north > 90) {
            throw new BadRequestException(fieldName + " latitud invalida. Rango permitido: -90..90");
        }
        if (west >= east) {
            throw new BadRequestException(fieldName + " invalido: west debe ser menor que east");
        }
        if (south >= north) {
            throw new BadRequestException(fieldName + " invalido: south debe ser menor que north");
        }
        return List.of(west, south, east, north);
    }

    private double requireFinite(Double value, String fieldName) {
        if (value == null || !Double.isFinite(value)) {
            throw new BadRequestException(fieldName + " debe ser numerico valido");
        }
        return value;
    }

    private boolean isValidBbox(List<Double> bbox) {
        if (bbox == null || bbox.size() != 4) {
            return false;
        }
        try {
            validateAndNormalizeAoiBbox(bbox, "aoiBbox");
            return true;
        } catch (BadRequestException ex) {
            return false;
        }
    }

    private String formatBbox(List<Double> bbox) {
        return bbox.get(0) + "," + bbox.get(1) + "," + bbox.get(2) + "," + bbox.get(3);
    }

    private Map<String, List<Double>> parseAoiMapFromEnv(String raw) {
        Map<String, List<Double>> result = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }

        String[] entries = raw.split(";");
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String[] pair = entry.trim().split(":", 2);
            if (pair.length != 2) {
                continue;
            }

            String code = pair[0].trim().toUpperCase();
            String[] coords = pair[1].trim().split(",");
            if (coords.length != 4) {
                continue;
            }

            try {
                List<Double> bbox = List.of(
                    Double.parseDouble(coords[0].trim()),
                    Double.parseDouble(coords[1].trim()),
                    Double.parseDouble(coords[2].trim()),
                    Double.parseDouble(coords[3].trim())
                );
                if (isValidBbox(bbox)) {
                    result.put(code, bbox);
                }
            } catch (NumberFormatException ignored) {
                // ignore invalid entries to keep coverage endpoint resilient
            }
        }
        return result;
    }
}
