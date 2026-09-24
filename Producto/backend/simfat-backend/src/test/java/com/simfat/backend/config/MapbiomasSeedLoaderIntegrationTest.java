package com.simfat.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaMapbiomasStats;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.ComunaMapbiomasStatsRepository;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.core.io.ClassPathResource;

/**
 * Mongo integration test for {@link MapbiomasSeedLoader} (S1b2). Requires a real MongoDB
 * (same setup as the other {@code @DataMongoTest} classes, e.g.
 * {@code BackfillComunaIdRunnerIntegrationTest}); not runnable without it. The event
 * ({@link ComunaGeometrySeededEvent}) is fired manually via {@code loader.loadSeed()}
 * (same pattern as {@code BackfillComunaIdRunnerIntegrationTest#backfill()}) instead of
 * through a full application context, so this stays a focused slice test.
 *
 * <p>Fixture strategy: the real shipped seed resource (86 fire-only 2017 documents) is
 * read directly in {@link #setUp} to discover the exact comunaIds it contains, and a
 * minimal {@link ComunaInfo} is created for each one — mirroring production, where
 * {@code MonitoredComunasConfig} has already seeded every real comuna by the time this
 * loader's event fires.
 */
@DataMongoTest
class MapbiomasSeedLoaderIntegrationTest {

    private static final String SEED_RESOURCE_PATH =
        "seed/mapbiomas/comuna-mapbiomas-stats.fuego-col1-2017-partial.jsonl";
    private static final int EXPECTED_DOCUMENT_COUNT = 86;

    @Autowired
    private ComunaMapbiomasStatsRepository statsRepository;
    @Autowired
    private ComunaInfoRepository comunaInfoRepository;

    // Plain instance, not autowired: @DataMongoTest only auto-configures Mongo-related
    // beans, not the web-layer Jackson ObjectMapper bean, and this test needs no
    // Spring-specific JSON configuration to parse the seed JSONL fixture.
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MapbiomasSeedLoader loader;
    private List<String> seedComunaIds;

    @BeforeEach
    void setUp() throws Exception {
        statsRepository.deleteAll();
        comunaInfoRepository.deleteAll();

        seedComunaIds = readSeedComunaIds();
        for (String comunaId : seedComunaIds) {
            ComunaInfo info = new ComunaInfo();
            info.setId(comunaId);
            info.setNombre("Comuna " + comunaId);
            info.setRegionId("biobio");
            comunaInfoRepository.save(info);
        }

        loader = new MapbiomasSeedLoader(statsRepository, comunaInfoRepository, objectMapper);
        loader.setSeedEnabled(true);
        loader.setDataVersion("fuego-col1@2017-partial");
    }

    private List<String> readSeedComunaIds() throws Exception {
        List<String> ids = new ArrayList<>();
        try (InputStream is = new ClassPathResource(SEED_RESOURCE_PATH).getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode node = objectMapper.readTree(line);
                ids.add(node.path("comunaId").asText());
            }
        }
        return ids;
    }

    @Test
    void loadSeed_populatesAllDocumentsWithDeterministicIds() {
        assertThat(seedComunaIds).hasSize(EXPECTED_DOCUMENT_COUNT);

        loader.loadSeed();

        List<ComunaMapbiomasStats> all = statsRepository.findAll();
        assertThat(all).hasSize(EXPECTED_DOCUMENT_COUNT);
        assertThat(all).allSatisfy(doc ->
            assertThat(doc.getId()).isEqualTo(doc.getComunaId() + "|fuego-col1@2017-partial"));
    }

    @Test
    void loadSeed_reRunning_isIdempotentNoDuplicatesNoDataLoss() {
        loader.loadSeed();
        assertThat(statsRepository.count()).isEqualTo(EXPECTED_DOCUMENT_COUNT);

        loader.loadSeed();

        assertThat(statsRepository.count()).isEqualTo(EXPECTED_DOCUMENT_COUNT);
    }

    @Test
    void loadSeed_doesNotTouchComunasCollection() {
        long comunaCountBefore = comunaInfoRepository.count();
        ComunaInfo before = comunaInfoRepository.findById(seedComunaIds.get(0)).orElseThrow();

        loader.loadSeed();

        assertThat(comunaInfoRepository.count()).isEqualTo(comunaCountBefore);
        ComunaInfo after = comunaInfoRepository.findById(seedComunaIds.get(0)).orElseThrow();
        assertThat(after.getNombre()).isEqualTo(before.getNombre());
        assertThat(after.getRegionId()).isEqualTo(before.getRegionId());
    }

    @Test
    void loadSeed_landCoverNullRecord_roundTripsCorrectlyAsFireOnlyPartial() {
        loader.loadSeed();

        ComunaMapbiomasStats doc = statsRepository
            .findByComunaIdAndDataVersion(seedComunaIds.get(0), "fuego-col1@2017-partial")
            .orElseThrow();

        assertThat(doc.getLandCover()).isNull();
        assertThat(doc.isPartial()).isTrue();
        assertThat(doc.getFire()).isNotNull();
        assertThat(doc.getFire().getAvailable()).isTrue();
        // S1b3: design D1/D3's burnedFractionPct, regenerated into the real committed seed
        // (mb_pipeline/build_stats.py::add_burned_fraction_pct); every comuna with
        // available=true must round-trip a non-null rank, never silently drop to null.
        assertThat(doc.getFire().getBurnedFractionPct()).isNotNull();
    }

    @Test
    void loadSeed_disabledViaFlag_skipsEntirelyNoSideEffects() {
        loader.setSeedEnabled(false);

        loader.loadSeed();

        assertThat(statsRepository.count()).isZero();
    }

    @Test
    void loadSeed_comunaMissingFromComunas_isSkippedNotFailed() {
        // Remove one comuna that the seed references; the loader must skip that record
        // and still load every other one, instead of aborting the whole run.
        String missingComunaId = seedComunaIds.get(0);
        comunaInfoRepository.deleteById(missingComunaId);

        loader.loadSeed();

        assertThat(statsRepository.count()).isEqualTo(EXPECTED_DOCUMENT_COUNT - 1);
        assertThat(statsRepository.findByComunaId(missingComunaId)).isEmpty();
    }

    @Test
    void loadSeed_realWorldStatusOk_isReflectedInSummary() {
        loader.loadSeed();

        MapbiomasSeedLoader.SeedLoadSummary summary = loader.getLastLoadSummary();
        assertThat(summary.status()).isEqualTo("ok");
        assertThat(summary.errors()).isZero();
        assertThat(summary.loaded()).isEqualTo(EXPECTED_DOCUMENT_COUNT);
    }

    @Test
    void loadSeed_everyLineFailsToParse_isNeverReportedAsStatusOk() {
        // Reproduces the failure class flagged for this slice: if a future schema drift
        // (a type change, not just an added field -- FAIL_ON_UNKNOWN_PROPERTIES only
        // protects against unknown fields) breaks every line, the per-line try/catch in
        // loadSeed() swallows every exception and nothing is persisted. Before this fix,
        // the final summary log line hardcoded "status=ok" regardless -- indistinguishable
        // from a healthy boot to an operator or an alerting rule keyed on that literal.
        loader.setSeedResourcePath("seed/mapbiomas/malformed-fixture.jsonl");

        loader.loadSeed();

        assertThat(statsRepository.count()).isZero();
        MapbiomasSeedLoader.SeedLoadSummary summary = loader.getLastLoadSummary();
        assertThat(summary.status()).isNotEqualTo("ok");
        assertThat(summary.status()).isEqualTo("failed");
        assertThat(summary.loaded()).isZero();
        assertThat(summary.errors()).isEqualTo(2);
    }
}
