package com.simfat.backend.service.mapbiomas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.model.ComunaMapbiomasStats;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * S1b4/S1b5 real-seed REGRESSION test (NOT just an evidence printout): pins the exact real
 * BEFORE (landCover=null, the pre-S1b4 production reality for all 86 comunas) and AFTER
 * (real landCover from the merged seed) fuelIndex/historyIndex/score/qualityFlag for 3 real
 * comunas, straight from the actual committed seed file -- never by hand.
 *
 * <p>Reads {@code src/main/resources/seed/mapbiomas/comuna-mapbiomas-stats.lulc-col2+fuego-col1-2013-2025.jsonl}
 * from disk (classpath-relative), uses 3 comunas with different real profiles (fire history,
 * agriculture-dominant, plantation-dominant), and calls the exact same package-private static
 * methods {@code ComunaRiskServiceImpl}/production code calls, via a real
 * {@link MapbiomasSusceptibilityServiceImpl} instance with a mocked repository returning the
 * real parsed document (BEFORE: same document with {@code landCover} forced to {@code null},
 * matching production before this change; AFTER: the document as-is).
 *
 * <p>S1b5 (2026-09-30) replaced the 2017-only partial fire section with the FULL 2013-2025
 * Fuego Collection 1 history: land cover (and therefore {@code fuelIndex}) is BYTE-IDENTICAL
 * to S1b4 for these 3 comunas (land cover did not change), but {@code historyIndex}/{@code
 * score} shift because the fire history term now reads the full 13-year union/frequency
 * instead of 2017-only. The expected values below were RE-CAPTURED from a real run against
 * the new committed seed (see the S1b5 apply-progress for the exact command and the old ->
 * new value table); a future change to the seed's real data or to the fuel/history/score
 * formulas that shifts any of these values is a REAL regression this test must catch, not
 * something to silently re-capture and re-paste.
 */
@ExtendWith(MockitoExtension.class)
class RealSeedRegressionTest {

    private static final String DATA_VERSION = "lulc-col2+fuego-col1@2013-2025";

    private static final Path SEED_PATH = Path.of(
        "src/main/resources/seed/mapbiomas/comuna-mapbiomas-stats.lulc-col2+fuego-col1-2013-2025.jsonl"
    );
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final double DELTA = 1e-4;

    @Mock
    private com.simfat.backend.repository.ComunaMapbiomasStatsRepository statsRepository;

    @Test
    void realSeedFuelHistoryScoreMatchRealComputedValues() throws IOException {
        FuelWeightTable fuelWeightTable = new FuelWeightTable(MAPPER);
        MapbiomasSusceptibilityServiceImpl service =
            new MapbiomasSusceptibilityServiceImpl(statsRepository, fuelWeightTable);
        service.setDataVersion(DATA_VERSION);

        List<String> lines = Files.readAllLines(SEED_PATH);
        assertEquals(86, lines.size(), "expected 86 real seed lines, got " + lines.size());

        // Cobquecura (Ñuble) is independently confirmed, straight from the real seed, as the
        // most plantation-heavy comuna (highest class 9 / Silviculture share) -- this
        // assertion is what proves CHL.13.2.1_1 below is still the right pick if the seed is
        // ever regenerated, rather than trusting a hardcoded id alone.
        assertEquals("CHL.13.2.1_1", findMostPlantationHeavyComuna(lines));

        // CHL.6.1.1_1 (Arauco, Biobío): S1b5 full 2013-2025 fire history -- burned in 2013,
        // 2014, 2015, 2016, 2019, 2020, 2023 and 2025 (yearLastFire=2025), vs the 2017-only
        // signal S1b4 saw. fuelIndex is BYTE-IDENTICAL to S1b4 (land cover unchanged);
        // historyIndex/score both shift because the history term now reads the full 13-year
        // union/frequency instead of the single 2017 year.
        assertComunaBusinessValues(
            lines, service, "CHL.6.1.1_1",
            0.78615, 0.38460352096670414, 0.6456087323383464
        );

        // CHL.13.1.1_1 (Bulnes, Ñuble): agriculture-dominant (class 18 ~53%). Burned in 2014,
        // 2016, 2017 and 2018 (yearLastFire=2018); 2017 is its largest fire year (1051 ha, 2.47%).
        // Its history index fell from 0.518 (S1b4) to 0.261 because of a percentile-population
        // shift, not a missing fire: burnedFractionPct went from 0.860 (ranking the 2017-only
        // fraction, when only ~38 comunas burned) to 0.430 (ranking the 2013-2025 union, where
        // Florida, Santa Juana and Tome exceed 50%).
        assertComunaBusinessValues(
            lines, service, "CHL.13.1.1_1",
            0.4716, 0.26055864156585223, 0.39776152454804825
        );

        // CHL.13.2.1_1 (Cobquecura, Ñuble): plantation-dominant. Its REAL
        // landCover.sharesByClass["9"] (class 9 = Silviculture) is 0.7941 -- the 0.89208
        // fuelIndex asserted here is a DIFFERENT number, the design-D3 weighted-share FUEL
        // INDEX (FuelWeightTable's per-class flammability weights applied to every class's
        // share, not the raw plantation share alone). Do not confuse the two.
        ComunaMapbiomasStats cobquecura = parseLine(lines, "CHL.13.2.1_1");
        assertEquals(
            0.7941,
            cobquecura.getLandCover().getSharesByClass().get("9"),
            DELTA,
            "Cobquecura's real sharesByClass[\"9\"] (plantation share) drifted"
        );
        // S1b5: Cobquecura burned in 2014 and 2023 (yearLastFire=2023) -- a real, if small,
        // fire signal, vs S1b4's near-zero 2017-only historyIndex.
        assertComunaBusinessValues(
            lines, service, "CHL.13.2.1_1",
            0.89208, 0.1259219059362537, 0.6239246670776888
        );
    }

    private String findMostPlantationHeavyComuna(List<String> lines) throws IOException {
        String best = null;
        double bestShare = -1.0;
        for (String line : lines) {
            JsonNode node = MAPPER.readTree(line);
            JsonNode landCover = node.path("landCover");
            if (landCover.isMissingNode() || landCover.isNull()) continue;
            double share = landCover.path("sharesByClass").path("9").asDouble(0.0);
            if (share > bestShare) {
                bestShare = share;
                best = node.path("comunaId").asText();
            }
        }
        return best;
    }

    // Minimal, test-local re-implementation of MapbiomasSeedLoader#mapRawRecord's landCover/
    // fire mapping (that method is package-private to com.simfat.backend.config): parses only
    // what this test needs, straight from the real seed line, never by hand.
    private ComunaMapbiomasStats parseLine(List<String> lines, String comunaId) throws IOException {
        for (String line : lines) {
            JsonNode node = MAPPER.readTree(line);
            if (!comunaId.equals(node.path("comunaId").asText())) {
                continue;
            }
            ComunaMapbiomasStats entity = new ComunaMapbiomasStats();
            entity.setComunaId(comunaId);
            entity.setDataVersion(DATA_VERSION);
            JsonNode landCoverNode = node.path("landCover");
            if (!landCoverNode.isMissingNode() && !landCoverNode.isNull()) {
                entity.setLandCover(MAPPER.treeToValue(landCoverNode, ComunaMapbiomasStats.LandCover.class));
            }
            JsonNode fireNode = node.path("fire");
            if (!fireNode.isMissingNode() && !fireNode.isNull()) {
                entity.setFire(MAPPER.treeToValue(fireNode, ComunaMapbiomasStats.Fire.class));
            }
            return entity;
        }
        throw new IllegalStateException("comuna not found in real seed: " + comunaId);
    }

    /**
     * Computes BEFORE (landCover forced null) and AFTER (real landCover) for {@code comunaId}
     * and asserts every real business value on both: BEFORE must show the pre-S1b4 degraded
     * shape ({@code fuelIndex=null}, {@code score=historyIndex},
     * {@code qualityFlag=MAPBIOMAS_FUEL_UNAVAILABLE}); AFTER must match the given expected
     * {@code fuelIndex}/{@code historyIndex}/{@code score} exactly (within {@link #DELTA}) with
     * no degradation flag.
     */
    private void assertComunaBusinessValues(
        List<String> lines,
        MapbiomasSusceptibilityServiceImpl service,
        String comunaId,
        double expectedFuel,
        double expectedHistory,
        double expectedScore
    ) throws IOException {
        ComunaMapbiomasStats real = parseLine(lines, comunaId);

        // BEFORE: same document, landCover forced null (the pre-S1b4 reality).
        ComunaMapbiomasStats before = copyWithoutLandCover(real);
        when(statsRepository.findByComunaIdAndDataVersion(comunaId, DATA_VERSION))
            .thenReturn(Optional.of(before));
        MapbiomasSusceptibility beforeResult = service.forComuna(comunaId).orElseThrow();

        assertNull(beforeResult.fuelIndex(), comunaId + " BEFORE fuelIndex must be null (no landCover)");
        assertEquals(expectedHistory, beforeResult.historyIndex(), DELTA, comunaId + " BEFORE historyIndex");
        assertEquals(expectedHistory, beforeResult.score(), DELTA, comunaId + " BEFORE score (history alone)");
        assertEquals(
            MapbiomasSusceptibility.MAPBIOMAS_FUEL_UNAVAILABLE, beforeResult.qualityFlag(),
            comunaId + " BEFORE qualityFlag"
        );

        // AFTER: the real document, landCover populated by S1b4.
        when(statsRepository.findByComunaIdAndDataVersion(comunaId, DATA_VERSION))
            .thenReturn(Optional.of(real));
        MapbiomasSusceptibility afterResult = service.forComuna(comunaId).orElseThrow();

        assertEquals(expectedFuel, afterResult.fuelIndex(), DELTA, comunaId + " AFTER fuelIndex");
        assertEquals(expectedHistory, afterResult.historyIndex(), DELTA, comunaId + " AFTER historyIndex");
        assertEquals(expectedScore, afterResult.score(), DELTA, comunaId + " AFTER score");
        assertNull(afterResult.qualityFlag(), comunaId + " AFTER qualityFlag (full D3 blend, no degradation)");

        System.out.printf(
            "%s: BEFORE score=%.4f (fuel unavailable) -> AFTER fuel=%.4f history=%.4f score=%.4f%n",
            comunaId, beforeResult.score(), afterResult.fuelIndex(), afterResult.historyIndex(), afterResult.score()
        );
    }

    private ComunaMapbiomasStats copyWithoutLandCover(ComunaMapbiomasStats real) {
        ComunaMapbiomasStats copy = new ComunaMapbiomasStats();
        copy.setComunaId(real.getComunaId());
        copy.setDataVersion(real.getDataVersion());
        copy.setLandCover(null);
        copy.setLandCoverReason("pre-S1b4: LULC not merged yet");
        copy.setFire(real.getFire());
        return copy;
    }
}
