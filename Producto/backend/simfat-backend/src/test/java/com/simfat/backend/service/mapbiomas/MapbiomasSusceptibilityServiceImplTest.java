package com.simfat.backend.service.mapbiomas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.model.ComunaMapbiomasStats;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * S2a1: standalone {@link MapbiomasSusceptibilityService}, reading {@code comuna_mapbiomas_stats}
 * (S1b2) and computing {@code S_mapbiomas} per design D3. Does NOT touch
 * {@code ComunaRiskServiceImpl} -- that wiring (the score blend, the wM=0 golden regression
 * test) is deferred to S2a2.
 *
 * <p>Decision Q26 (landCover null, the reality for all 86 comunas today, since only S1b2's
 * fire-only 2017 seed is loaded): the service does NOT skip/return-empty. It computes
 * {@code S_mapbiomas} from the fire-history component ALONE and sets an explicit
 * {@code qualityFlag} so no consumer mistakes this degraded signal for the full 0.65/0.35
 * blend.
 */
@ExtendWith(MockitoExtension.class)
class MapbiomasSusceptibilityServiceImplTest {

    private static final String DATA_VERSION = "fuego-col1@2017-partial";
    private static final String COMUNA_ID = "CHL.8.1.1_1";

    @Mock
    private com.simfat.backend.repository.ComunaMapbiomasStatsRepository statsRepository;

    private MapbiomasSusceptibilityServiceImpl service;

    @BeforeEach
    void setUp() {
        FuelWeightTable fuelWeightTable = new FuelWeightTable(new ObjectMapper());
        service = new MapbiomasSusceptibilityServiceImpl(statsRepository, fuelWeightTable);
        service.setDataVersion(DATA_VERSION);
        // Recency stays at its config default (R_MAX=0, neutral multiplier) unless a test
        // overrides it explicitly.
    }

    private ComunaMapbiomasStats.LandCover landCover(Map<String, Double> sharesByClass) {
        ComunaMapbiomasStats.LandCover landCover = new ComunaMapbiomasStats.LandCover();
        landCover.setReferenceYear(2024);
        landCover.setSharesByClass(sharesByClass);
        return landCover;
    }

    private ComunaMapbiomasStats.Fire fire(
        boolean available, Map<String, Double> burnedFractionByYear, Double frequencyMean, Integer yearsSinceLastFire
    ) {
        ComunaMapbiomasStats.Fire fire = new ComunaMapbiomasStats.Fire();
        fire.setAvailable(available);
        fire.setBurnedFractionByYear(burnedFractionByYear);
        fire.setFrequencyMean(frequencyMean);
        fire.setYearsSinceLastFire(yearsSinceLastFire);
        return fire;
    }

    private ComunaMapbiomasStats stats(ComunaMapbiomasStats.LandCover landCover, ComunaMapbiomasStats.Fire fire) {
        ComunaMapbiomasStats stats = new ComunaMapbiomasStats();
        stats.setComunaId(COMUNA_ID);
        stats.setDataVersion(DATA_VERSION);
        stats.setLandCover(landCover);
        stats.setFire(fire);
        return stats;
    }

    @Test
    void forComuna_noStatsDocument_returnsEmpty() {
        when(statsRepository.findByComunaIdAndDataVersion(eq(COMUNA_ID), eq(DATA_VERSION)))
            .thenReturn(Optional.empty());

        assertTrue(service.forComuna(COMUNA_ID).isEmpty());
    }

    @Test
    void forComuna_landCoverAndFirePresent_computesFullBlendWithKnownNumbers() {
        // fuel = 0.5*w(9=1.00) + 0.5*w(12=0.60) = 0.80
        Map<String, Double> shares = Map.of("9", 0.5, "12", 0.5);
        // burnedNorm = 0.1 (most recent year), recurrenceNorm = min(2.5,5)/5 = 0.5
        // history = clamp(0.60*0.1 + 0.40*0.5) * 1.0(recency neutral) = 0.26
        Map<String, Double> burned = Map.of("2020", 0.1);
        ComunaMapbiomasStats.Fire fire = fire(true, burned, 2.5, 3);
        ComunaMapbiomasStats.LandCover lc = landCover(shares);
        when(statsRepository.findByComunaIdAndDataVersion(eq(COMUNA_ID), eq(DATA_VERSION)))
            .thenReturn(Optional.of(stats(lc, fire)));

        MapbiomasSusceptibility result = service.forComuna(COMUNA_ID).orElseThrow();

        assertEquals(0.80, result.fuelIndex(), 1e-9);
        assertEquals(0.26, result.historyIndex(), 1e-9);
        // score = clamp(0.65*0.80 + 0.35*0.26) = 0.611
        assertEquals(0.611, result.score(), 1e-9);
        assertNull(result.qualityFlag());
        assertEquals(DATA_VERSION, result.dataVersion());
    }

    @Test
    void forComuna_landCoverNull_firePresent_returnsHistoryOnlyNotZeroPenalizedBlend() {
        Map<String, Double> burned = Map.of("2020", 0.1);
        ComunaMapbiomasStats.Fire fire = fire(true, burned, 2.5, 3);
        when(statsRepository.findByComunaIdAndDataVersion(eq(COMUNA_ID), eq(DATA_VERSION)))
            .thenReturn(Optional.of(stats(null, fire)));

        MapbiomasSusceptibility result = service.forComuna(COMUNA_ID).orElseThrow();

        assertNull(result.fuelIndex(), "fuel is unavailable, not silently zero");
        assertEquals(0.26, result.historyIndex(), 1e-9);
        // MUST be history alone (0.26), NOT 0.65*0 + 0.35*0.26 = 0.091 -- that would silently
        // penalize the score by the fuel weight for having no fuel data.
        assertEquals(0.26, result.score(), 1e-9);
        assertFalse(Math.abs(result.score() - 0.091) < 1e-6, "must not equal the wrongly fuel-penalized formula");
        assertEquals(MapbiomasSusceptibility.MAPBIOMAS_FUEL_UNAVAILABLE, result.qualityFlag());
    }

    @Test
    void forComuna_fireUnavailable_landCoverPresent_returnsFuelOnly() {
        Map<String, Double> shares = Map.of("9", 0.5, "12", 0.5);
        ComunaMapbiomasStats.Fire fire = fire(false, null, null, null);
        when(statsRepository.findByComunaIdAndDataVersion(eq(COMUNA_ID), eq(DATA_VERSION)))
            .thenReturn(Optional.of(stats(landCover(shares), fire)));

        MapbiomasSusceptibility result = service.forComuna(COMUNA_ID).orElseThrow();

        assertEquals(0.80, result.fuelIndex(), 1e-9);
        assertNull(result.historyIndex());
        // MUST be fuel alone (0.80), NOT 0.65*0.80 + 0.35*0 = 0.52.
        assertEquals(0.80, result.score(), 1e-9);
        assertEquals(MapbiomasSusceptibility.MAPBIOMAS_FIRE_UNAVAILABLE, result.qualityFlag());
    }

    @Test
    void forComuna_fireUnavailableAndLandCoverNull_returnsUnavailableFlag() {
        when(statsRepository.findByComunaIdAndDataVersion(eq(COMUNA_ID), eq(DATA_VERSION)))
            .thenReturn(Optional.of(stats(null, fire(false, null, null, null))));

        MapbiomasSusceptibility result = service.forComuna(COMUNA_ID).orElseThrow();

        assertNull(result.fuelIndex());
        assertNull(result.historyIndex());
        assertEquals(0.0, result.score(), 1e-9);
        assertEquals(MapbiomasSusceptibility.MAPBIOMAS_UNAVAILABLE, result.qualityFlag());
    }

    @Test
    void forComuna_unknownClassCodeInShares_throwsClearException() {
        Map<String, Double> shares = Map.of("999", 1.0);
        ComunaMapbiomasStats.Fire fire = fire(true, Map.of("2020", 0.1), 1.0, 1);
        when(statsRepository.findByComunaIdAndDataVersion(eq(COMUNA_ID), eq(DATA_VERSION)))
            .thenReturn(Optional.of(stats(landCover(shares), fire)));

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.forComuna(COMUNA_ID)
        );

        assertTrue(ex.getMessage().contains("999"));
    }

    @Test
    void forComuna_shareOfUnobservedClass27_isExcludedFromFuelSum() {
        // 0.3 share of class 27 (unobserved) must NOT count as zero-weighted fuel; only the
        // remaining classified shares contribute.
        Map<String, Double> shares = Map.of("9", 0.5, "27", 0.3, "12", 0.2);
        ComunaMapbiomasStats.Fire fire = fire(true, Map.of("2020", 0.0), 0.0, null);
        when(statsRepository.findByComunaIdAndDataVersion(eq(COMUNA_ID), eq(DATA_VERSION)))
            .thenReturn(Optional.of(stats(landCover(shares), fire)));

        MapbiomasSusceptibility result = service.forComuna(COMUNA_ID).orElseThrow();

        // fuel = 0.5*1.00 (class 9) + 0.2*0.60 (class 12) = 0.62; class 27's 0.3 share is
        // dropped entirely, not treated as 0.3*0.
        assertEquals(0.62, result.fuelIndex(), 1e-9);
    }
}
