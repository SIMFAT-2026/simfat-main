package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import com.simfat.backend.integration.openeo.OpenEoServiceClient;
import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaRiskSnapshot;
import com.simfat.backend.repository.CitizenReportRepository;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.ComunaRiskSnapshotRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import com.simfat.backend.service.NotificationService;
import com.simfat.backend.service.OpenWeatherFwiService;
import com.simfat.backend.service.mapbiomas.MapbiomasSusceptibilityService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Regression: snapshots persisted under a region id for comunas that no longer belong to it
// (e.g. Nuble comunas once stored under "biobio") must not leak into the regional read.
@ExtendWith(MockitoExtension.class)
class ComunaRiskServiceImplRegionScopeTest {

    @Mock private ComunaInfoRepository comunaRepository;
    @Mock private ComunaRiskSnapshotRepository snapshotRepository;
    @Mock private TerritoryWeatherObservationRepository weatherRepository;
    @Mock private CitizenReportRepository citizenReportRepository;
    @Mock private OpenWeatherFwiService fwiService;
    @Mock private OpenEoIndicatorObservationRepository openEoObsRepository;
    @Mock private NotificationService notificationService;
    @Mock private OpenEoServiceClient openEoServiceClient;
    @Mock private FirmsAttributionRouter firmsAttributionRouter;
    @Mock private MapbiomasSusceptibilityService mapbiomasService;

    private ComunaRiskServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ComunaRiskServiceImpl(comunaRepository, snapshotRepository, weatherRepository,
            citizenReportRepository, fwiService, openEoObsRepository, notificationService,
            openEoServiceClient, firmsAttributionRouter, mapbiomasService);
    }

    private ComunaRiskSnapshot snapshot(String comunaId, String regionId, LocalDateTime at) {
        ComunaRiskSnapshot s = new ComunaRiskSnapshot();
        s.setComunaId(comunaId);
        s.setRegionId(regionId);
        s.setComputedAt(at);
        return s;
    }

    private ComunaInfo comuna(String id) {
        ComunaInfo c = new ComunaInfo();
        c.setId(id);
        return c;
    }

    @Test
    void getLatestSnapshotsByRegion_excludesStaleSnapshotsOfComunasNoLongerInRegion() {
        LocalDateTime fresh = LocalDateTime.of(2026, 10, 13, 1, 30);
        LocalDateTime stale = LocalDateTime.of(2026, 6, 5, 1, 30);
        when(comunaRepository.findNamesByRegionId("biobio")).thenReturn(List.of(comuna("CHL.8.1_1")));
        // Sorted newest first, as the repository contract guarantees.
        when(snapshotRepository.findByRegionIdOrderByComputedAtDesc("biobio")).thenReturn(List.of(
            snapshot("CHL.8.1_1", "biobio", fresh),
            snapshot("CHL.16.1_1", "biobio", stale)
        ));

        Map<String, ComunaRiskSnapshot> result = service.getLatestSnapshotsByRegion("biobio");

        assertEquals(List.of("CHL.8.1_1"), List.copyOf(result.keySet()));
        assertFalse(result.containsKey("CHL.16.1_1"));
    }

    @Test
    void getLatestSnapshotsByRegion_emptyComunaInfo_returnsUnfilteredSnapshots() {
        LocalDateTime at = LocalDateTime.of(2026, 10, 13, 1, 30);
        when(comunaRepository.findNamesByRegionId("biobio")).thenReturn(List.of());
        when(snapshotRepository.findByRegionIdOrderByComputedAtDesc("biobio")).thenReturn(List.of(
            snapshot("CHL.8.1_1", "biobio", at),
            snapshot("CHL.16.1_1", "biobio", at)
        ));

        Map<String, ComunaRiskSnapshot> result = service.getLatestSnapshotsByRegion("biobio");

        assertEquals(List.of("CHL.8.1_1", "CHL.16.1_1"), List.copyOf(result.keySet()));
    }

    @Test
    void getLatestSnapshotsByRegion_duplicateInRegionComuna_newestWins() {
        ComunaRiskSnapshot fresh = snapshot("CHL.8.1_1", "biobio", LocalDateTime.of(2026, 10, 13, 1, 30));
        ComunaRiskSnapshot stale = snapshot("CHL.8.1_1", "biobio", LocalDateTime.of(2026, 6, 5, 1, 30));
        when(comunaRepository.findNamesByRegionId("biobio")).thenReturn(List.of(comuna("CHL.8.1_1")));
        when(snapshotRepository.findByRegionIdOrderByComputedAtDesc("biobio")).thenReturn(List.of(fresh, stale));

        Map<String, ComunaRiskSnapshot> result = service.getLatestSnapshotsByRegion("biobio");

        assertEquals(1, result.size());
        assertSame(fresh, result.get("CHL.8.1_1"));
    }

    @Test
    void getLatestSnapshotsListByRegion_appliesSameScoping() {
        LocalDateTime at = LocalDateTime.of(2026, 10, 13, 1, 30);
        when(comunaRepository.findNamesByRegionId("biobio")).thenReturn(List.of(comuna("CHL.8.1_1")));
        when(snapshotRepository.findByRegionIdOrderByComputedAtDesc("biobio")).thenReturn(List.of(
            snapshot("CHL.8.1_1", "biobio", at),
            snapshot("CHL.16.1_1", "biobio", at)
        ));

        List<ComunaRiskSnapshot> result = service.getLatestSnapshotsListByRegion("biobio");

        assertEquals(1, result.size());
        assertEquals("CHL.8.1_1", result.get(0).getComunaId());
    }

    @Test
    void getLatestSnapshotsByRegion_nullComunaId_isDroppedWhenFilterActive() {
        LocalDateTime at = LocalDateTime.of(2026, 10, 13, 1, 30);
        when(comunaRepository.findNamesByRegionId("biobio")).thenReturn(List.of(comuna("CHL.8.1_1")));
        when(snapshotRepository.findByRegionIdOrderByComputedAtDesc("biobio")).thenReturn(List.of(
            snapshot(null, "biobio", at),
            snapshot("CHL.8.1_1", "biobio", at)
        ));

        Map<String, ComunaRiskSnapshot> result = service.getLatestSnapshotsByRegion("biobio");

        assertEquals(List.of("CHL.8.1_1"), List.copyOf(result.keySet()));
    }
}
