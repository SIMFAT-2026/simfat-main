package com.simfat.backend.controller;

import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.MapbiomasComunaDetailDTO;
import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.model.ComunaMapbiomasStats;
import com.simfat.backend.repository.ComunaMapbiomasStatsRepository;
import com.simfat.backend.service.mapbiomas.MapbiomasSusceptibilityService;
import com.simfat.backend.web.MapbiomasComunaDetailBuilder;
import com.simfat.backend.web.MapbiomasComunaId;
import com.simfat.backend.web.MapbiomasYear;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (anonymous, GET-only) MapBiomas detail for one comuna (slice S3-5, design D5). It lives
 * under {@code /api/territory/public/**}, which is already allowlisted in
 * {@code PublicEndpointPaths}, so the default-deny contract is untouched.
 *
 * <p>Input discipline: {@code comunaId} must match the GID_3 format and {@code year} the LANDCOVER
 * range BEFORE any repository call; errors never echo the raw input. There is no server cache:
 * the lookup is one indexed read by a validated id, so there is no key space to bound.
 */
@RestController
@RequestMapping("/api/territory/public/mapbiomas")
public class MapbiomasComunaDetailController {

    private final ComunaMapbiomasStatsRepository statsRepository;
    private final MapbiomasSusceptibilityService susceptibilityService;

    @Value("${mapbiomas.stats.data-version:lulc-col2+fuego-col1@2013-2025}")
    private String dataVersion;

    @Value("${mapbiomas.citation:MapBiomas Chile - Coleccion 2 de Cobertura y Uso del Suelo (1999-2024) y Coleccion 1 de Fuego (2013-2025). Licencia CC BY 4.0.}")
    private String citation;

    // Configured wM, same property ComunaRiskServiceImpl reads: reported as-is so the UI can say
    // whether MapBiomas enters the risk score.
    @Value("${territory.riesgo.mapbiomas.weight:0.0}")
    private double mapbiomasWeight;

    public MapbiomasComunaDetailController(
        ComunaMapbiomasStatsRepository statsRepository,
        MapbiomasSusceptibilityService susceptibilityService
    ) {
        this.statsRepository = statsRepository;
        this.susceptibilityService = susceptibilityService;
    }

    @GetMapping("/{comunaId}")
    public ResponseEntity<ApiResponse<MapbiomasComunaDetailDTO>> getDetail(
        @PathVariable String comunaId,
        @RequestParam(required = false) String year
    ) {
        if (!MapbiomasComunaId.isValid(comunaId)) {
            throw new BadRequestException("El identificador de comuna no es valido");
        }
        MapbiomasYear resolved = MapbiomasYear.resolve(MapbiomasYear.Indicator.LANDCOVER, year);

        ComunaMapbiomasStats stats = statsRepository.findByComunaIdAndDataVersion(comunaId, dataVersion)
            .orElseThrow(() -> new ResourceNotFoundException("No hay datos de MapBiomas para la comuna solicitada"));

        MapbiomasComunaDetailDTO detail = MapbiomasComunaDetailBuilder.build(
            stats, resolved.year(), susceptibilityService.forComuna(comunaId), mapbiomasWeight, citation);

        return ResponseEntity.ok()
            .header("Cache-Control", "max-age=86400")
            .body(ApiResponse.ok("Detalle MapBiomas de la comuna obtenido correctamente", detail));
    }
}
