package com.simfat.backend.controller;

import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.HeatAlertRequestDTO;
import com.simfat.backend.dto.HeatAlertResponseDTO;
import com.simfat.backend.model.RiskLevel;
import com.simfat.backend.service.HeatAlertService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/alerts")
public class HeatAlertController {

    private final HeatAlertService heatAlertService;

    public HeatAlertController(HeatAlertService heatAlertService) {
        this.heatAlertService = heatAlertService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<HeatAlertResponseDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("Alertas obtenidas correctamente", heatAlertService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HeatAlertResponseDTO>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok("Alerta obtenida correctamente", heatAlertService.getById(id)));
    }

    @GetMapping("/region/{regionId}")
    public ResponseEntity<ApiResponse<List<HeatAlertResponseDTO>>> getByRegion(@PathVariable String regionId) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Alertas por region obtenidas correctamente",
            heatAlertService.getByRegion(regionId)
        ));
    }

    @GetMapping("/map")
    public ResponseEntity<ApiResponse<List<HeatAlertResponseDTO>>> getMap(
        @RequestParam(required = false) String regionId,
        @RequestParam(required = false) RiskLevel level,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDateTime fromDate = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDate = to != null ? to.atTime(23, 59, 59) : null;
        return ResponseEntity.ok(ApiResponse.ok(
            "Alertas para mapa obtenidas correctamente",
            heatAlertService.getMap(regionId, fromDate, toDate, level)
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HeatAlertResponseDTO>> create(@Valid @RequestBody HeatAlertRequestDTO event) {
        return ResponseEntity.ok(ApiResponse.ok("Alerta creada correctamente", heatAlertService.create(event)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HeatAlertResponseDTO>> update(
        @PathVariable String id,
        @Valid @RequestBody HeatAlertRequestDTO event
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Alerta actualizada correctamente", heatAlertService.update(id, event)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable String id) {
        heatAlertService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Alerta eliminada correctamente", id));
    }
}
