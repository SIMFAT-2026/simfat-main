package com.simfat.backend.controller;

import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.MapbiomasComunaDetailDTO;
import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.model.ComunaMapbiomasStats;
import com.simfat.backend.model.ComunaRiskSnapshot;
import com.simfat.backend.repository.ComunaMapbiomasStatsRepository;
import com.simfat.backend.repository.ComunaRiskSnapshotRepository;
import com.simfat.backend.service.mapbiomas.MapbiomasSusceptibility;
import com.simfat.backend.service.mapbiomas.MapbiomasSusceptibilityService;
import com.simfat.backend.web.MapbiomasComunaDetailBuilder;
import com.simfat.backend.web.MapbiomasComunaId;
import com.simfat.backend.web.MapbiomasLandCoverDefaultYearResolver;
import com.simfat.backend.web.MapbiomasYear;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * range BEFORE any repository call; errors never echo the raw input. There is no payload cache
 * (one indexed read by a validated id, so no key space to bound); the browser cache is short
 * (5 min) so a weight or dataVersion change propagates quickly. An omitted {@code year} resolves
 * to the same data-derived default as the LANDCOVER layer, cached per dataVersion.
 */
@RestController
@RequestMapping("/api/territory/public/mapbiomas")
public class MapbiomasComunaDetailController {

    private static final Logger log = LoggerFactory.getLogger(MapbiomasComunaDetailController.class);

    private final ComunaMapbiomasStatsRepository statsRepository;
    private final MapbiomasSusceptibilityService susceptibilityService;
    private final ComunaRiskSnapshotRepository snapshotRepository;

    @Value("${mapbiomas.stats.data-version:lulc-col2+fuego-col1@2013-2025}")
    private String dataVersion;

    @Value("${mapbiomas.citation:MapBiomas Chile - Colección 2 de Cobertura y Uso del Suelo (1999-2024) y Colección 1 de Fuego (2013-2025). Licencia CC BY 4.0.}")
    private String citation;

    // Configured wM, same property ComunaRiskServiceImpl reads. Only the fallback: the weight the
    // user sees is the one persisted in the comuna's latest risk snapshot.
    @Value("${territory.riesgo.mapbiomas.weight:0.0}")
    private double mapbiomasWeight;

    // Data-derived LANDCOVER default year, computed once per dataVersion (same idiom as
    // TerritoryController#resolveLandCoverDefaultYear).
    private volatile String cachedDefaultYearDataVersion;
    private volatile Integer cachedDefaultYear;

    public MapbiomasComunaDetailController(
        ComunaMapbiomasStatsRepository statsRepository,
        MapbiomasSusceptibilityService susceptibilityService,
        ComunaRiskSnapshotRepository snapshotRepository
    ) {
        this.statsRepository = statsRepository;
        this.susceptibilityService = susceptibilityService;
        this.snapshotRepository = snapshotRepository;
    }

    /** Test seam: the controller is a singleton, so the cached default year outlives a test. */
    void clearDefaultYearCache() {
        cachedDefaultYearDataVersion = null;
        cachedDefaultYear = null;
    }

    @GetMapping("/{comunaId}")
    public ResponseEntity<ApiResponse<MapbiomasComunaDetailDTO>> getDetail(
        @PathVariable String comunaId,
        @RequestParam(required = false) String year
    ) {
        if (!MapbiomasComunaId.isValid(comunaId)) {
            throw new BadRequestException("El identificador de comuna no es valido");
        }
        // The data-derived default is only read when the year is omitted (validation first).
        MapbiomasYear resolved = MapbiomasYear.resolve(
            MapbiomasYear.Indicator.LANDCOVER, year, year == null ? resolveDefaultYear() : MapbiomasYear.LANDCOVER_MAX_YEAR);

        ComunaMapbiomasStats stats = statsRepository.findByComunaIdAndDataVersion(comunaId, dataVersion)
            .orElseThrow(() -> new ResourceNotFoundException("No hay datos de MapBiomas para la comuna solicitada"));

        Optional<MapbiomasSusceptibility> susceptibility = Optional.empty();
        boolean indicesInvalid = false;
        try {
            susceptibility = susceptibilityService.forComuna(comunaId);
        } catch (IllegalArgumentException ex) {
            // Corrupt stored data must not turn a public read into a 500; no stack trace on purpose.
            indicesInvalid = true;
            log.warn("MapBiomas susceptibility data is invalid for comuna {}: {}", comunaId, ex.getMessage());
        }

        MapbiomasComunaDetailDTO detail = MapbiomasComunaDetailBuilder.build(
            stats, resolved.year(), susceptibility, effectiveWeight(comunaId), citation, indicesInvalid);

        return ResponseEntity.ok()
            .header("Cache-Control", "public, max-age=300")
            .body(ApiResponse.ok("Detalle MapBiomas de la comuna obtenido correctamente", detail));
    }

    /** wEff persisted in the comuna's latest snapshot (what the user sees); configured wM otherwise. */
    private double effectiveWeight(String comunaId) {
        try {
            return snapshotRepository.findTopByComunaIdOrderByComputedAtDesc(comunaId)
                .map(ComunaRiskSnapshot::getMapbiomasWeight)
                .orElse(mapbiomasWeight);
        } catch (RuntimeException ex) {
            log.warn("Risk snapshot lookup failed for comuna {}; using the configured MapBiomas weight", comunaId);
            return mapbiomasWeight;
        }
    }

    private synchronized int resolveDefaultYear() {
        if (cachedDefaultYear != null && dataVersion.equals(cachedDefaultYearDataVersion)) {
            return cachedDefaultYear;
        }
        List<ComunaMapbiomasStats> allStats = statsRepository.findByDataVersion(dataVersion);
        int resolved = MapbiomasLandCoverDefaultYearResolver.resolve(allStats);
        cachedDefaultYearDataVersion = dataVersion;
        cachedDefaultYear = resolved;
        return resolved;
    }
}
