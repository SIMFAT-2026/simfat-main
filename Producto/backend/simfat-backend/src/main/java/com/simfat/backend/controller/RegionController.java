package com.simfat.backend.controller;

import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.RegionAoiCoverageDTO;
import com.simfat.backend.dto.RegionAoiUpdateRequestDTO;
import com.simfat.backend.dto.RegionRequestDTO;
import com.simfat.backend.dto.RegionResponseDTO;
import com.simfat.backend.service.RegionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionService regionService;

    public RegionController(RegionService regionService) {
        this.regionService = regionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RegionResponseDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("Regiones obtenidas correctamente", regionService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RegionResponseDTO>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok("Region obtenida correctamente", regionService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RegionResponseDTO>> create(@Valid @RequestBody RegionRequestDTO region) {
        return ResponseEntity.ok(ApiResponse.ok("Region creada correctamente", regionService.create(region)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RegionResponseDTO>> update(
        @PathVariable String id,
        @Valid @RequestBody RegionRequestDTO region
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Region actualizada correctamente", regionService.update(id, region)));
    }

    @PatchMapping("/{id}/aoi")
    public ResponseEntity<ApiResponse<RegionResponseDTO>> updateAoi(
        @PathVariable String id,
        @RequestBody RegionAoiUpdateRequestDTO request
    ) {
        return ResponseEntity.ok(ApiResponse.ok("AOI de region actualizado correctamente", regionService.updateAoi(id, request)));
    }

    @GetMapping("/aoi/coverage")
    public ResponseEntity<ApiResponse<List<RegionAoiCoverageDTO>>> getAoiCoverage() {
        return ResponseEntity.ok(ApiResponse.ok("Cobertura AOI obtenida correctamente", regionService.getAoiCoverage()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable String id) {
        regionService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Region eliminada correctamente", id));
    }
}
