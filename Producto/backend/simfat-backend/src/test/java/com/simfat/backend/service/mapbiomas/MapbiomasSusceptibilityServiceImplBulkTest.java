package com.simfat.backend.service.mapbiomas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.model.ComunaMapbiomasStats;
import com.simfat.backend.repository.ComunaMapbiomasStatsRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * S2a2, NFR-7: {@link MapbiomasSusceptibilityService#forComunas} must read
 * {@code comuna_mapbiomas_stats} with a SINGLE bulk query
 * ({@link ComunaMapbiomasStatsRepository#findByDataVersion}) regardless of how many comunas
 * are requested, instead of one {@code findByComunaIdAndDataVersion} call per comuna (which
 * would reintroduce the N+1 pattern {@code ComunaRiskServiceImpl.recomputeAllComunas} loops
 * over -- see the {@code MapbiomasSusceptibilityServiceImpl} class Javadoc, "Bulk-read
 * deferral", which explicitly assigns this to S2a2).
 */
@ExtendWith(MockitoExtension.class)
class MapbiomasSusceptibilityServiceImplBulkTest {

    private static final String DATA_VERSION = "fuego-col1@2017-partial";

    @Mock
    private ComunaMapbiomasStatsRepository statsRepository;

    private MapbiomasSusceptibilityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MapbiomasSusceptibilityServiceImpl(statsRepository, new FuelWeightTable(new ObjectMapper()));
        service.setDataVersion(DATA_VERSION);
    }

    private ComunaMapbiomasStats fireOnlyStats(String comunaId, boolean available, double burnedFractionPct) {
        ComunaMapbiomasStats stats = new ComunaMapbiomasStats();
        stats.setComunaId(comunaId);
        stats.setDataVersion(DATA_VERSION);
        ComunaMapbiomasStats.Fire fire = new ComunaMapbiomasStats.Fire();
        fire.setAvailable(available);
        fire.setBurnedFractionByYear(Map.of("2017", 0.1));
        fire.setBurnedFractionPct(burnedFractionPct);
        fire.setFrequencyMean(1.0);
        fire.setYearsSinceLastFire(3);
        stats.setFire(fire);
        return stats;
    }

    @Test
    void forComunas_issuesExactlyOneBulkQuery_regardlessOfRequestedCount() {
        when(statsRepository.findByDataVersion(DATA_VERSION)).thenReturn(List.of(
            fireOnlyStats("comuna-A", true, 0.4),
            fireOnlyStats("comuna-B", true, 0.9)
        ));

        Map<String, MapbiomasSusceptibility> result =
            service.forComunas(List.of("comuna-A", "comuna-B", "comuna-C", "comuna-D", "comuna-E"));

        verify(statsRepository, times(1)).findByDataVersion(DATA_VERSION);
        verify(statsRepository, never()).findByComunaIdAndDataVersion(any(), any());
        assertThat(result).containsOnlyKeys("comuna-A", "comuna-B");
    }

    @Test
    void forComunas_missingComuna_isAbsentFromTheResultMap_neverThrows() {
        when(statsRepository.findByDataVersion(DATA_VERSION)).thenReturn(List.of(fireOnlyStats("comuna-A", true, 0.4)));

        Map<String, MapbiomasSusceptibility> result = service.forComunas(List.of("comuna-A", "comuna-missing"));

        assertThat(result).containsKey("comuna-A");
        assertThat(result).doesNotContainKey("comuna-missing");
    }

    @Test
    void forComunas_ignoresStatsRowsNotInTheRequestedSet() {
        when(statsRepository.findByDataVersion(DATA_VERSION)).thenReturn(List.of(
            fireOnlyStats("comuna-A", true, 0.4),
            fireOnlyStats("comuna-unrequested", true, 0.9)
        ));

        Map<String, MapbiomasSusceptibility> result = service.forComunas(List.of("comuna-A"));

        assertThat(result).containsOnlyKeys("comuna-A");
    }

    @Test
    void forComunas_sameScoreAs_forComuna_forTheSameStatsRow() {
        ComunaMapbiomasStats stats = fireOnlyStats("comuna-A", true, 0.4);
        when(statsRepository.findByDataVersion(DATA_VERSION)).thenReturn(List.of(stats));
        when(statsRepository.findByComunaIdAndDataVersion("comuna-A", DATA_VERSION)).thenReturn(Optional.of(stats));

        MapbiomasSusceptibility single = service.forComuna("comuna-A").orElseThrow();
        MapbiomasSusceptibility bulk = service.forComunas(List.of("comuna-A")).get("comuna-A");

        assertThat(bulk.score()).isEqualTo(single.score());
        assertThat(bulk.historyIndex()).isEqualTo(single.historyIndex());
        assertThat(bulk.qualityFlag()).isEqualTo(single.qualityFlag());
        assertThat(bulk.dataVersion()).isEqualTo(single.dataVersion());
    }

    @Test
    void forComunas_emptyInput_stillIssuesTheBulkQuery_andReturnsEmptyMap() {
        when(statsRepository.findByDataVersion(DATA_VERSION)).thenReturn(List.of());

        Map<String, MapbiomasSusceptibility> result = service.forComunas(List.of());

        assertThat(result).isEmpty();
    }
}
