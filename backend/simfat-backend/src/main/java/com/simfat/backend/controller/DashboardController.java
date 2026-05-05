package com.simfat.backend.controller;

import com.simfat.backend.dto.AlertsSummaryDTO;
import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.CriticalRegionDTO;
import com.simfat.backend.dto.DataFreshnessDTO;
import com.simfat.backend.dto.DashboardSummaryDTO;
import com.simfat.backend.dto.IndicatorLatestDTO;
import com.simfat.backend.dto.IndicatorMapResponseDTO;
import com.simfat.backend.dto.IndicatorSeriesDTO;
import com.simfat.backend.dto.LossTrendPointDTO;
import com.simfat.backend.dto.SyncRunResponseDTO;
import com.simfat.backend.model.IndicatorType;
import com.simfat.backend.service.DashboardIndicatorService;
import com.simfat.backend.service.DashboardService;
import com.simfat.backend.service.OpenEoSyncService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardIndicatorService dashboardIndicatorService;
    private final OpenEoSyncService openEoSyncService;

    public DashboardController(
        DashboardService dashboardService,
        DashboardIndicatorService dashboardIndicatorService,
        OpenEoSyncService openEoSyncService
    ) {
        this.dashboardService = dashboardService;
        this.dashboardIndicatorService = dashboardIndicatorService;
        this.openEoSyncService = openEoSyncService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryDTO>> getSummary() {
        return ResponseEntity.ok(ApiResponse.ok("Resumen de dashboard obtenido correctamente", dashboardService.getSummary()));
    }

    @GetMapping("/critical-regions")
    public ResponseEntity<ApiResponse<List<CriticalRegionDTO>>> getCriticalRegions() {
        return ResponseEntity.ok(ApiResponse.ok(
            "Regiones criticas obtenidas correctamente",
            dashboardService.getCriticalRegions()
        ));
    }

    @GetMapping("/loss-trend")
    public ResponseEntity<ApiResponse<List<LossTrendPointDTO>>> getLossTrend() {
        return ResponseEntity.ok(ApiResponse.ok("Tendencia de perdida obtenida correctamente", dashboardService.getLossTrend()));
    }

    @GetMapping("/alerts-summary")
    public ResponseEntity<ApiResponse<AlertsSummaryDTO>> getAlertsSummary() {
        return ResponseEntity.ok(ApiResponse.ok(
            "Resumen de alertas obtenido correctamente",
            dashboardService.getAlertsSummary()
        ));
    }

    @PostMapping("/sync/run")
    public ResponseEntity<ApiResponse<SyncRunResponseDTO>> runSync(
        @RequestParam(required = false) String regionId,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Sync dashboard ejecutado correctamente",
            openEoSyncService.runSync(regionId, from, to)
        ));
    }

    @GetMapping("/indicators/latest")
    public ResponseEntity<ApiResponse<IndicatorLatestDTO>> getLatestIndicator(
        @RequestParam String regionId,
        @RequestParam String indicator
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Ultimo valor de indicador obtenido correctamente",
            dashboardIndicatorService.getLatest(regionId, IndicatorType.from(indicator))
        ));
    }

    @GetMapping("/indicators/series")
    public ResponseEntity<ApiResponse<IndicatorSeriesDTO>> getIndicatorSeries(
        @RequestParam String regionId,
        @RequestParam String indicator,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(defaultValue = "day") String granularity
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Serie de indicador obtenida correctamente",
            dashboardIndicatorService.getSeries(regionId, IndicatorType.from(indicator), from, to, granularity)
        ));
    }

    @GetMapping("/indicators/map")
    public ResponseEntity<ApiResponse<IndicatorMapResponseDTO>> getIndicatorMap(
        @RequestParam String indicator,
        @RequestParam String from,
        @RequestParam String to,
        @RequestParam(defaultValue = "200") Integer limit
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Capa de mapa de indicador obtenida correctamente",
            dashboardIndicatorService.getMap(IndicatorType.from(indicator), from, to, limit)
        ));
    }

    @GetMapping("/data-freshness")
    public ResponseEntity<ApiResponse<DataFreshnessDTO>> getDataFreshness(@RequestParam String regionId) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Frescura de datos obtenida correctamente",
            dashboardIndicatorService.getDataFreshness(regionId)
        ));
    }
}
