package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.simfat.backend.model.ComunaRiskSnapshot;
import com.simfat.backend.repository.ComunaRiskSnapshotRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// Requires a local/CI MongoDB at the URI configured in
// src/test/resources/application.properties (no embedded Mongo in this
// project) — validates the @Aggregation pipeline syntax actually runs,
// which a Mockito-only test cannot catch.
@SpringBootTest
class ComunaRiskSnapshotRepositoryIntegrationTest {

    @Autowired
    private ComunaRiskSnapshotRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void countComunasWithHighOrCriticalAlertLevel_countsDistinctComunasByLatestSnapshotOnly() {
        // comuna-1: oldest snapshot is CRITICO, but the LATEST is NORMAL — must
        // NOT be counted (regression for "counts ALL history" bug class).
        saveSnapshot("comuna-1", "biobio", "CRITICO", LocalDateTime.now().minusDays(2));
        saveSnapshot("comuna-1", "biobio", "NORMAL", LocalDateTime.now());

        // comuna-2: latest snapshot is ALTO — must be counted.
        saveSnapshot("comuna-2", "biobio", "ALTO", LocalDateTime.now());

        // comuna-3: latest snapshot is CRITICO — must be counted.
        saveSnapshot("comuna-3", "araucania", "CRITICO", LocalDateTime.now());

        // comuna-4: latest snapshot is PREVENTIVO — must NOT be counted.
        saveSnapshot("comuna-4", "araucania", "PREVENTIVO", LocalDateTime.now());

        Long count = repository.countComunasWithHighOrCriticalAlertLevel();

        assertEquals(2L, count);
    }

    @Test
    void countComunasWithHighOrCriticalAlertLevel_noMatches_returnsNull() {
        saveSnapshot("comuna-1", "biobio", "NORMAL", LocalDateTime.now());

        Long count = repository.countComunasWithHighOrCriticalAlertLevel();

        // $count on an empty result set produces no document at all, so
        // Spring Data maps it to null — callers must default to 0 (see
        // DashboardServiceImplTest.getSummary_totalAlertas_nullAggregation_defaultsToZero).
        assertEquals(null, count);
    }

    private void saveSnapshot(String comunaId, String regionId, String alertLevel, LocalDateTime computedAt) {
        ComunaRiskSnapshot snapshot = new ComunaRiskSnapshot();
        snapshot.setComunaId(comunaId);
        snapshot.setRegionId(regionId);
        snapshot.setAlertLevel(alertLevel);
        snapshot.setComputedAt(computedAt);
        snapshot.setScoreComposite(0.5);
        snapshot.setMode("STANDARD");
        repository.save(snapshot);
    }
}
