package com.simfat.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.CitizenReportPayloadDTO;
import com.simfat.backend.dto.CitizenReportResponseDTO;
import com.simfat.backend.dto.CitizenReportStatusPatchDTO;
import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.model.CitizenReport;
import com.simfat.backend.model.CitizenReportStatus;
import com.simfat.backend.repository.CitizenReportRepository;
import com.simfat.backend.service.SupabaseStorageService;
import com.simfat.backend.service.impl.LocalImageFallbackStorageService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/citizen-reports")
public class CitizenReportController {

    private final CitizenReportRepository citizenReportRepository;
    private final ObjectMapper objectMapper;
    private final SupabaseStorageService storageService;
    private final LocalImageFallbackStorageService localImageFallbackStorageService;

    public CitizenReportController(
        CitizenReportRepository citizenReportRepository,
        ObjectMapper objectMapper,
        SupabaseStorageService storageService,
        LocalImageFallbackStorageService localImageFallbackStorageService
    ) {
        this.citizenReportRepository = citizenReportRepository;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
        this.localImageFallbackStorageService = localImageFallbackStorageService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CitizenReportResponseDTO>>> getAll(
        @RequestParam(required = false) String regionId,
        @RequestParam(required = false) CitizenReportStatus status,
        @RequestParam(required = false) String category
    ) {
        List<CitizenReportResponseDTO> items = citizenReportRepository.findAll()
            .stream()
            .filter(item -> regionId == null || regionId.isBlank() || regionId.equals(item.getRegionId()))
            .filter(item -> status == null || status == item.getStatus())
            .filter(item -> category == null || category.isBlank() || category.equalsIgnoreCase(item.getCategory()))
            .sorted((a, b) -> {
                LocalDateTime left = a.getCreatedAt() == null ? LocalDateTime.MIN : a.getCreatedAt();
                LocalDateTime right = b.getCreatedAt() == null ? LocalDateTime.MIN : b.getCreatedAt();
                return right.compareTo(left);
            })
            .map(this::toResponse)
            .toList();

        return ResponseEntity.ok(ApiResponse.ok("Reportes ciudadanos obtenidos correctamente", items));
    }

    @PostMapping(consumes = { "multipart/form-data" })
    public ResponseEntity<ApiResponse<CitizenReportResponseDTO>> create(
        @RequestPart("payload") String payload,
        @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        CitizenReportPayloadDTO dto = parsePayload(payload);

        CitizenReport report = new CitizenReport();
        report.setRegionId(dto.getRegionId());
        report.setCategory(dto.getCategory().toUpperCase());
        report.setDescription(dto.getDescription());
        report.setLatitude(dto.getLatitude());
        report.setLongitude(dto.getLongitude());
        report.setStatus(CitizenReportStatus.RECIBIDO);
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());

        if (files != null && !files.isEmpty()) {
            report.setPhotos(resolvePhotoReferences(files));
        }

        CitizenReport created = citizenReportRepository.save(report);
        return ResponseEntity.ok(ApiResponse.ok("Reporte ciudadano creado correctamente", toResponse(created)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CitizenReportResponseDTO>> patchStatus(
        @PathVariable String id,
        @Valid @RequestBody CitizenReportStatusPatchDTO patch
    ) {
        CitizenReport existing = citizenReportRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Reporte ciudadano no encontrado con id: " + id));

        existing.setStatus(patch.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());

        CitizenReport updated = citizenReportRepository.save(existing);
        return ResponseEntity.ok(ApiResponse.ok("Estado de reporte actualizado correctamente", toResponse(updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable String id) {
        CitizenReport existing = citizenReportRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Reporte ciudadano no encontrado con id: " + id));

        citizenReportRepository.delete(existing);
        return ResponseEntity.ok(ApiResponse.ok("Reporte ciudadano eliminado correctamente", id));
    }

    private CitizenReportPayloadDTO parsePayload(String rawPayload) {
        try {
            String normalized = rawPayload == null ? "" : rawPayload.trim();
            try {
                return objectMapper.readValue(normalized, CitizenReportPayloadDTO.class);
            } catch (IOException first) {
                String unescaped = objectMapper.readValue(normalized, String.class);
                return objectMapper.readValue(unescaped, CitizenReportPayloadDTO.class);
            }
        } catch (IOException ex) {
            throw new BadRequestException("Payload de reporte ciudadano invalido");
        }
    }

    private List<String> resolvePhotoReferences(List<MultipartFile> files) {
        return files.stream()
            .map(this::safeUploadReference)
            .filter(Objects::nonNull)
            .filter(value -> !value.isBlank())
            .toList();
    }

    private String safeUploadReference(MultipartFile file) {
        try {
            String reference = storageService.uploadCitizenReportFile(file);
            if (reference != null && !reference.isBlank()) {
                return reference;
            }
        } catch (RuntimeException ex) {
            // En modo demo priorizamos continuidad y visibilidad local de adjuntos.
        }
        String localReference = localImageFallbackStorageService.storeCitizenReportFile(file);
        return toAbsoluteReference(localReference);
    }

    private String toAbsoluteReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return reference;
        }
        if (reference.startsWith("http://") || reference.startsWith("https://") || reference.startsWith("data:") || reference.startsWith("blob:")) {
            return reference;
        }
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        if (reference.startsWith("/")) {
            return baseUrl + reference;
        }
        return baseUrl + "/" + reference;
    }

    private CitizenReportResponseDTO toResponse(CitizenReport item) {
        CitizenReportResponseDTO dto = new CitizenReportResponseDTO();
        dto.setId(item.getId());
        dto.setRegionId(item.getRegionId());
        dto.setCategory(item.getCategory());
        dto.setDescription(item.getDescription());
        dto.setLatitude(item.getLatitude());
        dto.setLongitude(item.getLongitude());
        dto.setStatus(item.getStatus());
        dto.setPhotoCount(item.getPhotos() == null ? 0 : item.getPhotos().size());
        dto.setPhotos(item.getPhotos());
        dto.setCreatedAt(item.getCreatedAt());
        return dto;
    }
}
