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
 * S1b4 real-seed REGRESSION test (NOT just an evidence printout): pins the exact real
 * BEFORE (landCover=null, the pre-S1b4 production reality for all 86 comunas) and AFTER
 * (real landCover from the merged seed) fuelIndex/historyIndex/score/qualityFlag for 3 real
 * comunas, straight from the actual committed seed file -- never by hand.
 *
 * <p>Reads {@code src/main/resources/seed/mapbiomas/comuna-mapbiomas-stats.lulc-col2+fuego-col1-2017-partial.jsonl}
 * from disk (classpath-relative), uses 3 comunas with different real profiles (fire history,
 * agriculture-dominant, plantation-dominant), and calls the exact same package-private static
 * methods {@code ComunaRiskServiceImpl}/production code calls, via a real
 * {@link MapbiomasSusceptibilityServiceImpl} instance with a mocked repository returning the
 * real parsed document (BEFORE: same document with {@code landCover} forced to {@code null},
 * matching production before this change; AFTER: the document as-is).
 *
 * <p>The expected values below were captured from a real run against the committed seed
 * (see the S1b4 apply-progress for the exact command); a future change to the seed's real
 * data or to the fuel/history/score formulas that shifts any of these values is a REAL
 * regression this test must catch, not something to silently re-capture and re-paste.
 */
@ExtendWith(MockitoExtension.class)
class RealSeedRegressionTest {

    private static final String DATA_VERSION = "lulc-col2+fuego-col1@2017-partial";

    private static final Path SEED_PATH = Path.of(
        "src/main/resources/seed/mapbiomas/comuna-mapbiomas-stats.lulc-col2+fuego-col1-2017-partial.jsonl"
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

        // CHL.6.1.1_1 (Arauco, Biobío): real 2017 fire history (burned in 2017).
        assertComunaBusinessValues(
            lines, service, "CHL.6.1.1_1",
            0.78615, 0.005496633761944296, 0.5129213218166805
        );

        // CHL.13.1.1_1 (Bulnes, Ñuble): agriculture-dominant (class 18 ~53%), no fire signal.
        assertComunaBusinessValues(
            lines, service, "CHL.13.1.1_1",
            0.4716, 0.5184502467571859, 0.48802358636501497
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
        assertComunaBusinessValues(
            lines, service, "CHL.13.2.1_1",
            0.89208, 9.331152665051137e-5, 0.5798846590343277
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
