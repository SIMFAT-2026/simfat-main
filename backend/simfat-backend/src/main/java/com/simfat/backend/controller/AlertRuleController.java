package com.simfat.backend.controller;

import com.simfat.backend.dto.AlertRuleRequestDTO;
import com.simfat.backend.dto.AlertRuleResponseDTO;
import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.service.AlertRuleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    public AlertRuleController(AlertRuleService alertRuleService) {
        this.alertRuleService = alertRuleService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AlertRuleResponseDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("Reglas obtenidas correctamente", alertRuleService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AlertRuleResponseDTO>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok("Regla obtenida correctamente", alertRuleService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AlertRuleResponseDTO>> create(@Valid @RequestBody AlertRuleRequestDTO rule) {
        return ResponseEntity.ok(ApiResponse.ok("Regla creada correctamente", alertRuleService.create(rule)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AlertRuleResponseDTO>> update(
        @PathVariable String id,
        @Valid @RequestBody AlertRuleRequestDTO rule
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Regla actualizada correctamente", alertRuleService.update(id, rule)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable String id) {
        alertRuleService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Regla eliminada correctamente", id));
    }
}
