package com.simfat.backend.service.mapbiomas;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * S1b4 evidence artifact (NOT a regression gate -- prints, does not assert business values):
 * computes real BEFORE (landCover=null, the pre-S1b4 production reality for all 86 comunas)
 * vs AFTER (real landCover from the merged seed) fuel/history/score/unobservedShare for 3
 * real comunas, straight from the actual committed seed file -- never by hand.
 *
 * <p>Reads {@code src/main/resources/seed/mapbiomas/comuna-mapbiomas-stats.lulc-col2+fuego-col1-2017-partial.jsonl}
 * from disk (classpath-relative), picks 3 comunas with different real profiles (plantation-
 * heavy, native-forest-heavy, and one that actually burned in 2017), and calls the exact same
 * package-private static methods {@code ComunaRiskServiceImpl}/production code calls, via a
 * real {@link MapbiomasSusceptibilityServiceImpl} instance with a mocked repository returning
 * the real parsed document (BEFORE: same document with {@code landCover} forced to
 * {@code null}, matching production before this change; AFTER: the document as-is).
 */
@ExtendWith(MockitoExtension.class)
class RealSeedBeforeAfterReportTest {

    private static final Path SEED_PATH = Path.of(
        "src/main/resources/seed/mapbiomas/comuna-mapbiomas-stats.lulc-col2+fuego-col1-2017-partial.jsonl"
    );
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Mock
    private com.simfat.backend.repository.ComunaMapbiomasStatsRepository statsRepository;

    @Test
    void printsBeforeAfterForThreeRealComunas() throws IOException {
        FuelWeightTable fuelWeightTable = new FuelWeightTable(MAPPER);
        MapbiomasSusceptibilityServiceImpl service =
            new MapbiomasSusceptibilityServiceImpl(statsRepository, fuelWeightTable);
        service.setDataVersion("lulc-col2+fuego-col1@2017-partial");

        List<String> lines = Files.readAllLines(SEED_PATH);
        assertTrue(lines.size() == 86, "expected 86 real seed lines, got " + lines.size());

        // 3 real comunas picked for contrasting real profiles found in the actual seed:
        // - CHL.6.1.1_1 (Arauco, Biobío): real 2017 fire history (burned in 2017).
        // - CHL.13.1.1_1 (Bulnes, Ñuble): agriculture-dominant (class 18 ~53%), no fire signal shown here on purpose.
        // - a plantation-heavy comuna discovered by scanning for the highest class-9 (Silviculture) share.
        String[] targets = {"CHL.6.1.1_1", "CHL.13.1.1_1", findMostPlantationHeavyComuna(lines)};

        for (String comunaId : targets) {
            ComunaMapbiomasStats real = parseLine(lines, comunaId);
            report(comunaId, real, service, statsRepository);
        }
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
    // what this report needs, straight from the real seed line, never by hand.
    private ComunaMapbiomasStats parseLine(List<String> lines, String comunaId) throws IOException {
        for (String line : lines) {
            JsonNode node = MAPPER.readTree(line);
            if (!comunaId.equals(node.path("comunaId").asText())) {
                continue;
            }
            ComunaMapbiomasStats entity = new ComunaMapbiomasStats();
            entity.setComunaId(comunaId);
            entity.setDataVersion("lulc-col2+fuego-col1@2017-partial");
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

    private void report(
        String comunaId,
        ComunaMapbiomasStats real,
        MapbiomasSusceptibilityServiceImpl service,
        com.simfat.backend.repository.ComunaMapbiomasStatsRepository statsRepository
    ) {
        // BEFORE: same document, landCover forced null (the pre-S1b4 reality).
        ComunaMapbiomasStats before = copyWithoutLandCover(real);
        when(statsRepository.findByComunaIdAndDataVersion(comunaId, "lulc-col2+fuego-col1@2017-partial"))
            .thenReturn(Optional.of(before));
        MapbiomasSusceptibility beforeResult = service.forComuna(comunaId).orElseThrow();

        // AFTER: the real document, landCover populated by S1b4.
        when(statsRepository.findByComunaIdAndDataVersion(comunaId, "lulc-col2+fuego-col1@2017-partial"))
            .thenReturn(Optional.of(real));
        MapbiomasSusceptibility afterResult = service.forComuna(comunaId).orElseThrow();

        System.out.println("=== " + comunaId + " ===");
        System.out.printf(
            "BEFORE: fuel=%s history=%s score=%.4f unobservedShare=%s qualityFlag=%s%n",
            beforeResult.fuelIndex(), beforeResult.historyIndex(), beforeResult.score(),
            beforeResult.unobservedShare(), beforeResult.qualityFlag()
        );
        System.out.printf(
            "AFTER:  fuel=%.4f history=%s score=%.4f unobservedShare=%s qualityFlag=%s%n",
            afterResult.fuelIndex(), afterResult.historyIndex(), afterResult.score(),
            afterResult.unobservedShare(), afterResult.qualityFlag()
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
