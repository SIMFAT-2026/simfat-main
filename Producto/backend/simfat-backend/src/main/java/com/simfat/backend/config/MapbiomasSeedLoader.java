package com.simfat.backend.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads the committed MapBiomas seed (JSONL, one document per line) into the
 * {@code comuna_mapbiomas_stats} collection (S1b2, design D1). Listens for
 * {@link ComunaGeometrySeededEvent} — the same event {@link BackfillComunaIdRunner}
 * already uses — so {@link ComunaInfo} documents exist first: this loader enriches each
 * seed record with {@code regionId}/{@code nombreComuna} read from
 * {@link ComunaInfoRepository} instead of duplicating that data inside the generated seed
 * file (single source of truth, mirrors the pattern already established by
 * {@code BackfillComunaIdRunner}).
 *
 * <p>Never touches the {@code comunas} collection. Upserts by the deterministic id
 * {@link ComunaMapbiomasStats#buildId} so re-running (e.g. every boot) is idempotent —
 * same fetch-mutate-save idiom as {@code MonitoredComunasConfig.seedFromGeoJson}.
 *
 * <p>Honesty note: the currently shipped seed resource is fire-only (Fuego Colección 1,
 * 2017), {@code landCover=null}, {@code partial=true} — there is no merged LULC+Fuego
 * seed yet (see class javadoc on {@link ComunaMapbiomasStats}).
 */
@Component
public class MapbiomasSeedLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapbiomasSeedLoader.class);

    private static final String SEED_RESOURCE_PATH =
        "seed/mapbiomas/comuna-mapbiomas-stats.fuego-col1-2017-partial.jsonl";

    private final ComunaMapbiomasStatsRepository statsRepository;
    private final ComunaInfoRepository comunaInfoRepository;
    private final ObjectMapper objectMapper;

    @Value("${mapbiomas.seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${mapbiomas.seed.data-version:fuego-col1@2017-partial}")
    private String dataVersion;

    public MapbiomasSeedLoader(
        ComunaMapbiomasStatsRepository statsRepository,
        ComunaInfoRepository comunaInfoRepository,
        ObjectMapper objectMapper
    ) {
        this.statsRepository = statsRepository;
        this.comunaInfoRepository = comunaInfoRepository;
        // Copy, don't mutate, the injected mapper: this loader's own instance is made
        // tolerant of unrecognized JSON fields (FAIL_ON_UNKNOWN_PROPERTIES=false) so a
        // future field the Python pipeline adds to the seed doesn't silently zero out the
        // whole load again the way the missing fire.reason field just did (every one of the
        // 86 seed lines failed mapRawRecord and was swallowed by loadSeed()'s per-line
        // catch). This is scoped ONLY to seed parsing -- the REST API's Jackson
        // configuration (e.g. the injected bean itself, used elsewhere in the app) is left
        // untouched, so unexpected fields on real API payloads still fail loudly there.
        this.objectMapper = objectMapper.copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // Test seam: @Value fields are normally only set by Spring; tests construct this
    // component directly (same idiom as BackfillComunaIdRunner#setBackfillEnabled).
    void setSeedEnabled(boolean seedEnabled) {
        this.seedEnabled = seedEnabled;
    }

    void setDataVersion(String dataVersion) {
        this.dataVersion = dataVersion;
    }

    @EventListener(ComunaGeometrySeededEvent.class)
    public void loadSeed() {
        if (!seedEnabled) {
            LOGGER.info("mapbiomas_seed status=skipped reason=disabled");
            return;
        }

        ClassPathResource resource = new ClassPathResource(SEED_RESOURCE_PATH);
        if (!resource.exists()) {
            LOGGER.warn("mapbiomas_seed status=file_not_found path={}", SEED_RESOURCE_PATH);
            return;
        }

        long loaded = 0;
        long skipped = 0;
        long errors = 0;
        List<ComunaMapbiomasStats> toSave = new ArrayList<>();

        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    JsonNode raw = objectMapper.readTree(line);
                    String comunaId = raw.path("comunaId").asText();
                    Optional<ComunaInfo> comunaInfo = comunaInfoRepository.findById(comunaId);
                    if (comunaInfo.isEmpty()) {
                        LOGGER.warn("mapbiomas_seed status=skipped_no_comuna comunaId={}", comunaId);
                        skipped++;
                        continue;
                    }

                    ComunaMapbiomasStats entity = mapRawRecord(raw, comunaInfo.get(), dataVersion, objectMapper);
                    toSave.add(entity);
                    loaded++;
                } catch (Exception ex) {
                    LOGGER.warn("mapbiomas_seed status=error line_error={}", ex.getMessage());
                    errors++;
                }
            }
        } catch (Exception ex) {
            LOGGER.error("mapbiomas_seed status=failed error={}", ex.getMessage(), ex);
            return;
        }

        statsRepository.saveAll(toSave);
        LOGGER.info(
            "mapbiomas_seed status=ok dataVersion={} loaded={} skipped={} errors={}",
            dataVersion, loaded, skipped, errors
        );
    }

    // Pure conversion: raw seed JSON line + the comuna it belongs to -> the persisted
    // entity. Package-private + static so it is unit-testable without Mongo or Spring.
    static ComunaMapbiomasStats mapRawRecord(
        JsonNode raw, ComunaInfo comunaInfo, String dataVersion, ObjectMapper objectMapper
    ) throws com.fasterxml.jackson.core.JsonProcessingException {
        String comunaId = raw.path("comunaId").asText();

        ComunaMapbiomasStats entity = new ComunaMapbiomasStats();
        entity.setId(ComunaMapbiomasStats.buildId(comunaId, dataVersion));
        entity.setComunaId(comunaId);
        entity.setDataVersion(dataVersion);
        entity.setRegionId(comunaInfo.getRegionId());
        entity.setNombreComuna(comunaInfo.getNombre());

        JsonNode landCoverNode = raw.path("landCover");
        if (landCoverNode.isMissingNode() || landCoverNode.isNull()) {
            entity.setLandCover(null);
        } else {
            entity.setLandCover(objectMapper.treeToValue(landCoverNode, ComunaMapbiomasStats.LandCover.class));
        }
        entity.setLandCoverReason(textOrNull(raw, "landCoverReason"));

        JsonNode fireNode = raw.path("fire");
        if (fireNode.isMissingNode() || fireNode.isNull()) {
            entity.setFire(null);
        } else {
            entity.setFire(objectMapper.treeToValue(fireNode, ComunaMapbiomasStats.Fire.class));
        }
        entity.setFireReason(textOrNull(raw, "fireReason"));

        entity.setPartial(raw.path("partial").asBoolean(false));

        JsonNode provenanceNode = raw.path("provenance");
        if (!provenanceNode.isMissingNode() && !provenanceNode.isNull()) {
            entity.setProvenance(objectMapper.treeToValue(provenanceNode, ComunaMapbiomasStats.Provenance.class));
        }

        JsonNode computedAtNode = raw.path("computedAt");
        if (!computedAtNode.isMissingNode() && !computedAtNode.isNull()) {
            entity.setComputedAt(java.time.Instant.parse(normalizeInstant(computedAtNode.asText())));
        }

        return entity;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return (value.isMissingNode() || value.isNull()) ? null : value.asText();
    }

    // The Python pipeline emits microsecond-precision offsets like
    // "2026-09-21T18:17:35.689908+00:00"; Instant.parse requires the "Z" suffix form.
    private static String normalizeInstant(String value) {
        return value.endsWith("+00:00") ? value.substring(0, value.length() - 6) + "Z" : value;
    }
}
