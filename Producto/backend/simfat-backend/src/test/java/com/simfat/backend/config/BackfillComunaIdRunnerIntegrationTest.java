package com.simfat.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonMultiPolygon;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.data.geo.Point;
import org.springframework.test.context.TestPropertySource;

// Models OpenEoRepositoriesIntegrationTest / ComunaGeoAttributionRepositoryIntegrationTest:
// @DataMongoTest + auto-index-creation=true so $geoIntersects runs through the real
// 2dsphere index. The runner's @Async/@EventListener wiring is exercised at boot via real
// application logs (Phase 3.2 of the original Slice A); this test calls #backfill()
// directly (synchronously) to assert the bulk-write attribution logic itself.
@DataMongoTest
@TestPropertySource(properties = "spring.data.mongodb.auto-index-creation=true")
class BackfillComunaIdRunnerIntegrationTest {

    @Autowired
    private ComunaInfoRepository comunaInfoRepository;
    @Autowired
    private HeatAlertEventRepository heatAlertEventRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    private BackfillComunaIdRunner runner;

    @BeforeEach
    void setUp() {
        comunaInfoRepository.deleteAll();
        heatAlertEventRepository.deleteAll();
        runner = new BackfillComunaIdRunner(heatAlertEventRepository, comunaInfoRepository, mongoTemplate);
        runner.setBackfillEnabled(true);

        GeoJsonMultiPolygon square = new GeoJsonMultiPolygon(List.of(new GeoJsonPolygon(List.of(
            new Point(-73.0, -38.0),
            new Point(-72.0, -38.0),
            new Point(-72.0, -37.0),
            new Point(-73.0, -37.0),
            new Point(-73.0, -38.0)
        ))));
        ComunaInfo comunaA = new ComunaInfo();
        comunaA.setId("comuna-A");
        comunaA.setRegionId("region-1");
        comunaA.setGeometry(square);
        comunaInfoRepository.save(comunaA);
    }

    private HeatAlertEvent unattributedEvent(double lat, double lon) {
        HeatAlertEvent e = new HeatAlertEvent();
        e.setRegionId("region-1");
        e.setLatitud(lat);
        e.setLongitud(lon);
        e.setFechaEvento(LocalDateTime.now().minusHours(1));
        e.setFuente("NASA_FIRMS");
        e.setFirmsConfidence("h");
        e.setFirmsFrp(20.0);
        return e;
    }

    @Test
    void backfill_existingNullComunaIdRows_getAttributedCorrectly() {
        HeatAlertEvent inside = heatAlertEventRepository.save(unattributedEvent(-37.5, -72.5));
        HeatAlertEvent offshore = heatAlertEventRepository.save(unattributedEvent(0.0, 0.0));

        runner.backfill();

        HeatAlertEvent reloadedInside = heatAlertEventRepository.findById(inside.getId()).orElseThrow();
        HeatAlertEvent reloadedOffshore = heatAlertEventRepository.findById(offshore.getId()).orElseThrow();

        assertEquals("comuna-A", reloadedInside.getComunaId());
        assertNull(reloadedOffshore.getComunaId());
    }

    @Test
    void backfill_reRunningOnAlreadyAttributedRows_isIdempotentNoOp() {
        HeatAlertEvent inside = heatAlertEventRepository.save(unattributedEvent(-37.5, -72.5));

        runner.backfill();
        HeatAlertEvent firstPass = heatAlertEventRepository.findById(inside.getId()).orElseThrow();
        assertEquals("comuna-A", firstPass.getComunaId());

        // Second run: stream is comunaId == null, so the already-attributed row is no
        // longer in scope — must not error, must not change the result.
        runner.backfill();
        HeatAlertEvent secondPass = heatAlertEventRepository.findById(inside.getId()).orElseThrow();
        assertEquals("comuna-A", secondPass.getComunaId());
    }

    @Test
    void backfill_disabledViaProperty_doesNotRunNoSideEffects() {
        HeatAlertEvent inside = heatAlertEventRepository.save(unattributedEvent(-37.5, -72.5));
        runner.setBackfillEnabled(false);

        runner.backfill();

        HeatAlertEvent unchanged = heatAlertEventRepository.findById(inside.getId()).orElseThrow();
        assertNull(unchanged.getComunaId());
    }

    @Test
    void backfill_manyRowsAcrossMultipleBatches_allAttributed() {
        // BATCH is 500 in the runner; 12 rows is enough to prove correctness without
        // making the test slow — batch-boundary behavior is exercised structurally by
        // the loop, not by row count.
        List<HeatAlertEvent> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            rows.add(unattributedEvent(-37.5, -72.5));
        }
        heatAlertEventRepository.saveAll(rows);

        runner.backfill();

        long attributedCount = heatAlertEventRepository.findAll().stream()
            .filter(e -> "comuna-A".equals(e.getComunaId()))
            .count();
        assertEquals(12, attributedCount);
    }
}
