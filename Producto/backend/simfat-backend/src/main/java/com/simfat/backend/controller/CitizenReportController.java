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
import com.simfat.backend.service.ObjectStorageService;
import com.simfat.backend.service.impl.LocalObjectFallbackStorageService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    private static final String STORAGE_NAMESPACE = "citizen-reports";

    // Catalogo cerrado de subcategorias por categoria. Espejo manual de
    // REPORT_SUBCATEGORIES en CitizenReportsPage.jsx - si Pablo trae una lista
    // nueva, hay que actualizar ambos lados.
    private static final Map<String, List<String>> SUBCATEGORIES_BY_CATEGORY = Map.of(
        "IGNICION_POTENCIAL", List.of(
            "Quemas agrícolas",
            "Fogatas en zonas no habilitadas",
            "Parrillas o cocinillas en áreas vegetadas",
            "Quema de basura",
            "Quema de residuos forestales",
            "Uso de maquinaria que genera chispas",
            "Faenas de soldadura al aire libre",
            "Trabajos con esmeriles o galleteras",
            "Vehículos estacionados sobre pastizales secos",
            "Colillas de cigarro / personas fumando",
            "Fuegos artificiales",
            "Actividades recreativas con fuego",
            "Actos intencionales o sospecha de incendio provocado"
        ),
        "INFRAESTRUCTURA_ELECTRICA", List.of(
            "Cables eléctricos caídos",
            "Cables en contacto con vegetación",
            "Postes en mal estado",
            "Chispas provenientes de tendidos eléctricos",
            "Transformadores defectuosos",
            "Árboles con riesgo de caer sobre líneas eléctricas"
        ),
        "COMBUSTIBLE_VEGETAL", List.of(
            "Pastizales secos sin manejo",
            "Acumulación de ramas y residuos forestales",
            "Microbasurales con material combustible",
            "Sitios eriazos abandonados",
            "Plantaciones con exceso de material seco",
            "Matorrales densos cercanos a viviendas",
            "Ausencia de cortafuegos",
            "Cortafuegos deteriorados o interrumpidos",
            "Regeneración de especies exóticas creciendo sin control"
        )
    );

    private final CitizenReportRepository citizenReportRepository;
    private final ObjectMapper objectMapper;
    private final ObjectStorageService storageService;
    private final LocalObjectFallbackStorageService localObjectFallbackStorageService;

    public CitizenReportController(
        CitizenReportRepository citizenReportRepository,
        ObjectMapper objectMapper,
        ObjectStorageService storageService,
        LocalObjectFallbackStorageService localObjectFallbackStorageService
    ) {
        this.citizenReportRepository = citizenReportRepository;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
        this.localObjectFallbackStorageService = localObjectFallbackStorageService;
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
    @PreAuthorize("hasAnyAuthority('PERM_REPORT_CREATE','ROLE_VERIFIED_USER','ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CitizenReportResponseDTO>> create(
        @RequestPart("payload") String payload,
        @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        CitizenReportPayloadDTO dto = parsePayload(payload);
        if (dto.getComunaId() == null || dto.getComunaId().isBlank()) {
            throw new BadRequestException("La comuna es obligatoria");
        }
        String category = dto.getCategory().toUpperCase();

        CitizenReport report = new CitizenReport();
        report.setRegionId(dto.getRegionId());
        report.setComunaId(dto.getComunaId());
        report.setCategory(category);
        report.setSubCategory(resolveSubCategory(category, dto.getSubCategory()));
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
    @PreAuthorize("hasAnyAuthority('PERM_REPORT_MODERATE','ROLE_MODERATOR','ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CitizenReportResponseDTO>> patchStatus(
        @PathVariable String id,
        @Valid @RequestBody CitizenReportStatusPatchDTO patch
    ) {
        CitizenReport existing = citizenReportRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Reporte ciudadano no encontrado con id: " + id));

        existing.setStatus(patch.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        if (patch.getStatus() == CitizenReportStatus.VALIDADO) {
            existing.setValidatedAt(LocalDateTime.now());
            existing.setStaleSince(null);
        }

        CitizenReport updated = citizenReportRepository.save(existing);
        return ResponseEntity.ok(ApiResponse.ok("Estado de reporte actualizado correctamente", toResponse(updated)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_REPORT_MODERATE','ROLE_MODERATOR','ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable String id) {
        CitizenReport existing = citizenReportRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Reporte ciudadano no encontrado con id: " + id));

        citizenReportRepository.delete(existing);
        return ResponseEntity.ok(ApiResponse.ok("Reporte ciudadano eliminado correctamente", id));
    }

    private String resolveSubCategory(String category, String rawSubCategory) {
        List<String> options = SUBCATEGORIES_BY_CATEGORY.get(category);
        if (options == null) {
            return null;
        }

        if (rawSubCategory == null || rawSubCategory.isBlank()) {
            throw new BadRequestException("La subcategoria es obligatoria para la categoria " + category);
        }

        return options.stream()
            .filter(option -> option.equalsIgnoreCase(rawSubCategory.trim()))
            .findFirst()
            .orElseThrow(() -> new BadRequestException("Subcategoria invalida para la categoria " + category));
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
            String reference = storageService.uploadFile(file, STORAGE_NAMESPACE);
            if (reference != null && !reference.isBlank()) {
                return reference;
            }
        } catch (RuntimeException ex) {
            // En modo demo priorizamos continuidad y visibilidad local de adjuntos.
        }
        String localReference = localObjectFallbackStorageService.storeFile(file, STORAGE_NAMESPACE);
        String absoluteReference = toAbsoluteReference(localReference);
        if (absoluteReference == null || absoluteReference.isBlank()) {
            throw new BadRequestException("No fue posible persistir el archivo adjunto");
        }
        return absoluteReference;
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
        dto.setComunaId(item.getComunaId());
        dto.setCategory(item.getCategory());
        dto.setSubCategory(item.getSubCategory());
        dto.setDescription(item.getDescription());
        dto.setLatitude(item.getLatitude());
        dto.setLongitude(item.getLongitude());
        dto.setStatus(item.getStatus());
        dto.setPhotoCount(item.getPhotos() == null ? 0 : item.getPhotos().size());
        dto.setPhotos(item.getPhotos());
        dto.setCreatedAt(item.getCreatedAt());
        dto.setValidatedAt(item.getValidatedAt());
        dto.setStaleCount(item.getStaleCount());
        dto.setStaleSince(item.getStaleSince());
        dto.setDiscardReason(item.getDiscardReason());
        return dto;
    }
}
