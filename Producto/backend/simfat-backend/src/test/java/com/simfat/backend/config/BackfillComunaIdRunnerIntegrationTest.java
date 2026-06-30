package com.simfat.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.RiskLevel;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.geo.GeoJsonMultiPolygon;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

// Backfill scenarios from spec.md ("Backfill attributes existing rows
// correctly", "Backfill is idempotent and re-runnable"). Exercises the real
// BackfillComunaIdRunner.backfill() method against the real Mongo configured
// for tests (no embedded Mongo in this project, see design.md Decision 5).
@DataMongoTest
@TestPropertySource(properties = "spring.data.mongodb.auto-index-creation=true")
class BackfillComunaIdRunnerIntegrationTest {

    @Autowired
    private ComunaInfoRepository comunaInfoRepository;
    @Autowired
    private HeatAlertEventRepository heatAlertEventRepository;

    private BackfillComunaIdRunner runner;

    @BeforeEach
    void setUp() {
        comunaInfoRepository.deleteAll();
        heatAlertEventRepository.deleteAll();
        runner = new BackfillComunaIdRunner(heatAlertEventRepository, comunaInfoRepository);
        ReflectionTestUtils.setField(runner, "backfillEnabled", true);

        ComunaInfo comunaA = new ComunaInfo();
        comunaA.setId("comuna-a");
        comunaA.setNombre("Comuna A");
        comunaA.setRegionId("biobio");
        comunaA.setGeometry(squarePolygon(0.0, 0.0, 1.0, 1.0));
        comunaInfoRepository.save(comunaA);
    }

    @AfterEach
    void tearDown() {
        comunaInfoRepository.deleteAll();
        heatAlertEventRepository.deleteAll();
    }

    @Test
    void backfill_attributesExistingRowsByPointInPolygon_orLeavesNullForOffshore() {
        HeatAlertEvent inside = newEvent(0.5, 0.5);
        HeatAlertEvent offshore = newEvent(50.0, 50.0);
        heatAlertEventRepository.saveAll(List.of(inside, offshore));

        runner.backfill();

        HeatAlertEvent reloadedInside = heatAlertEventRepository.findById(inside.getId()).orElseThrow();
        HeatAlertEvent reloadedOffshore = heatAlertEventRepository.findById(offshore.getId()).orElseThrow();

        assertEquals("comuna-a", reloadedInside.getComunaId());
        assertNull(reloadedOffshore.getComunaId());
    }

    @Test
    void backfill_reRunOnAlreadyAttributedRows_isNoOp_noErrors() {
        HeatAlertEvent inside = newEvent(0.5, 0.5);
        heatAlertEventRepository.save(inside);

        runner.backfill();
        String firstRunComunaId = heatAlertEventRepository.findById(inside.getId()).orElseThrow().getComunaId();

        runner.backfill();
        String secondRunComunaId = heatAlertEventRepository.findById(inside.getId()).orElseThrow().getComunaId();

        assertEquals(firstRunComunaId, secondRunComunaId);
        assertEquals("comuna-a", secondRunComunaId);
    }

    private HeatAlertEvent newEvent(double lon, double lat) {
        HeatAlertEvent event = new HeatAlertEvent();
        event.setRegionId("biobio");
        event.setLatitud(lat);
        event.setLongitud(lon);
        event.setNivelRiesgo(RiskLevel.MEDIO);
        event.setFuente("NASA_FIRMS");
        event.setFechaEvento(LocalDateTime.now());
        // comunaId intentionally left unset to simulate pre-existing rows.
        return event;
    }

    private GeoJsonMultiPolygon squarePolygon(double west, double south, double east, double north) {
        GeoJsonPolygon square = new GeoJsonPolygon(
            new Point(west, south),
            new Point(east, south),
            new Point(east, north),
            new Point(west, north),
            new Point(west, south));
        return new GeoJsonMultiPolygon(List.of(square));
    }
}
