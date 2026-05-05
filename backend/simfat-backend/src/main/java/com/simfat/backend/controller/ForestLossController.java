package com.simfat.backend.controller;

import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.ForestLossRequestDTO;
import com.simfat.backend.dto.ForestLossResponseDTO;
import com.simfat.backend.service.ForestLossService;
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
@RequestMapping("/api/forest-loss")
public class ForestLossController {

    private final ForestLossService forestLossService;

    public ForestLossController(ForestLossService forestLossService) {
        this.forestLossService = forestLossService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ForestLossResponseDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("Registros forestales obtenidos correctamente", forestLossService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ForestLossResponseDTO>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok("Registro forestal obtenido correctamente", forestLossService.getById(id)));
    }

    @GetMapping("/region/{regionId}")
    public ResponseEntity<ApiResponse<List<ForestLossResponseDTO>>> getByRegion(@PathVariable String regionId) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Registros forestales por region obtenidos correctamente",
            forestLossService.getByRegion(regionId)
        ));
    }

    @GetMapping("/year/{year}")
    public ResponseEntity<ApiResponse<List<ForestLossResponseDTO>>> getByYear(@PathVariable Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Registros forestales por anio obtenidos correctamente",
            forestLossService.getByYear(year)
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ForestLossResponseDTO>> create(@Valid @RequestBody ForestLossRequestDTO record) {
        return ResponseEntity.ok(ApiResponse.ok("Registro forestal creado correctamente", forestLossService.create(record)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ForestLossResponseDTO>> update(
        @PathVariable String id,
        @Valid @RequestBody ForestLossRequestDTO record
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Registro forestal actualizado correctamente",
            forestLossService.update(id, record)
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable String id) {
        forestLossService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Registro forestal eliminado correctamente", id));
    }
}
