package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.Region;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.RegionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.geo.GeoJsonMultiPolygon;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;

// Post-review (FIX 1, FIX 2, FIX 6) regression coverage for the extracted
// FirmsAttributionRouter — the single sanctioned source of FIRMS-by-attribution reads for
// both risk services. These tests defend the corrected Architectural Invariant 3 (comuna
// granularity, not region granularity) and the corrected fallback candidate pool sourcing
// (never pre-filtered by persisted regionId).
@ExtendWith(MockitoExtension.class)
class FirmsAttributionRouterTest {

    @Mock
    private ComunaInfoRepository comunaInfoRepository;
    @Mock
    private HeatAlertEventRepository heatAlertEventRepository;
    @Mock
    private RegionRepository regionRepository;

    private FirmsAttributionRouter router;

    @BeforeEach
    void setUp() {
        router = new FirmsAttributionRouter(comunaInfoRepository, heatAlertEventRepository, regionRepository);
    }

    private ComunaInfo comuna(String id, String regionId, double lat, double lon) {
        ComunaInfo c = new ComunaInfo();
        c.setId(id);
        c.setRegionId(regionId);
        c.setCenterLat(lat);
        c.setCenterLon(lon);
        return c;
    }

    private ComunaInfo coveredComuna(String id, String regionId, double lat, double lon) {
        ComunaInfo c = comuna(id, regionId, lat, lon);
        c.setGeometry(new GeoJsonMultiPolygon(List.of(new GeoJsonPolygon(List.of(
            new Point(-73.0, -38.0),
            new Point(-72.0, -38.0),
            new Point(-72.0, -37.0),
            new Point(-73.0, -38.0)
        )))));
        return c;
    }

    private HeatAlertEvent firmsEvent(String regionId, String comunaId, double lat, double lon, LocalDateTime fecha, double frp) {
        HeatAlertEvent e = new HeatAlertEvent();
        e.setRegionId(regionId);
        e.setComunaId(comunaId);
        e.setLatitud(lat);
        e.setLongitud(lon);
        e.setFechaEvento(fecha);
        e.setFuente("NASA_FIRMS");
        e.setFirmsConfidence("h");
        e.setFirmsFrp(frp);
        return e;
    }

    // ---- FIX 1 (findings C1+C5): comuna-granularity coverage probe ----

    @Test
    void resolveForComuna_uncoveredComuna_usesCentroidFallbackNotZero() {
        ComunaInfo comuna = comuna("comuna-A", "region-X", -37.5, -72.5);
        LocalDateTime since = LocalDateTime.now().minusHours(48);

        HeatAlertEvent foco = firmsEvent("region-X", null, -37.5, -72.5, LocalDateTime.now().minusHours(20), 30.0);
        when(heatAlertEventRepository.findByFuenteAndFechaEventoAfter("NASA_FIRMS", since)).thenReturn(List.of(foco));
        when(comunaInfoRepository.findByRegionId("region-X")).thenReturn(List.of(comuna));

        List<HeatAlertEvent> result = router.resolveForComuna(comuna, since);

        assertEquals(1, result.size());
        verify(heatAlertEventRepository, never()).findByComunaIdAndFechaEventoAfter(any(), any());
    }

    // THE incident regression at comuna granularity: a region can be mostly covered (9/10
    // comunas with valid geometry) while ONE comuna's own GADM polygon failed validation
    // (Decision 3, geometry=null for just that one). That single comuna must still use the
    // centroid fallback — routing must never be decided at region granularity.
    @Test
    void resolveForComuna_thisComunaHasNullGeometryEvenThoughSiblingsAreCovered_usesCentroidFallbackNotZero() {
        ComunaInfo uncoveredComuna = comuna("comuna-bad-polygon", "region-mostly-covered", -37.5, -72.5);
        ComunaInfo coveredSibling = coveredComuna("comuna-good-polygon", "region-mostly-covered", -38.5, -73.5);
        LocalDateTime since = LocalDateTime.now().minusHours(48);

        when(comunaInfoRepository.findByRegionId("region-mostly-covered"))
            .thenReturn(List.of(uncoveredComuna, coveredSibling));

        HeatAlertEvent foco = firmsEvent("region-mostly-covered", null, -37.5, -72.5, LocalDateTime.now().minusHours(20), 30.0);
        when(heatAlertEventRepository.findByFuenteAndFechaEventoAfter("NASA_FIRMS", since)).thenReturn(List.of(foco));

        List<HeatAlertEvent> result = router.resolveForComuna(uncoveredComuna, since);

        assertEquals(1, result.size());
        verify(heatAlertEventRepository, never()).findByComunaIdAndFechaEventoAfter(any(), any());
    }

    @Test
    void resolveForComuna_coveredComuna_queriesByPersistedComunaIdCentroidNeverInvoked() {
        ComunaInfo comuna = coveredComuna("comuna-A", "region-X", -37.5, -72.5);
        LocalDateTime since = LocalDateTime.now().minusHours(48);

        HeatAlertEvent foco = firmsEvent("region-X", "comuna-A", -37.5, -72.5, LocalDateTime.now().minusHours(20), 30.0);
        when(heatAlertEventRepository.findByComunaIdAndFechaEventoAfter("comuna-A", since)).thenReturn(List.of(foco));

        List<HeatAlertEvent> result = router.resolveForComuna(comuna, since);

        assertEquals(1, result.size());
        verify(heatAlertEventRepository, never()).findByFuenteAndFechaEventoAfter(any(), any());
        verify(comunaInfoRepository, never()).findByRegionId(any());
    }

    // ---- FIX 1 (region-level split, region-level analog of C1) ----

    @Test
    void resolveForRegion_mixedCoverage_sumsGeometricAndCentroidContributions() {
        String regionId = "region-mixed";
        ComunaInfo coveredComuna = coveredComuna("comuna-covered", regionId, -37.0, -72.0);
        ComunaInfo uncoveredComuna = comuna("comuna-uncovered", regionId, -38.5, -73.5);
        when(comunaInfoRepository.findByRegionId(regionId)).thenReturn(List.of(coveredComuna, uncoveredComuna));

        LocalDateTime since = LocalDateTime.now().minusHours(48);

        HeatAlertEvent geometricEvent = firmsEvent(regionId, "comuna-covered", -37.0, -72.0, LocalDateTime.now().minusHours(10), 20.0);
        when(heatAlertEventRepository.findByComunaIdInAndFechaEventoAfter(eq(List.of("comuna-covered")), eq(since)))
            .thenReturn(List.of(geometricEvent));

        HeatAlertEvent fallbackEvent = firmsEvent(regionId, null, -38.5, -73.5, LocalDateTime.now().minusHours(10), 20.0);
        when(heatAlertEventRepository.findByFuenteAndFechaEventoAfter("NASA_FIRMS", since)).thenReturn(List.of(fallbackEvent));

        Region region = new Region();
        region.setId(regionId);
        region.setAoiBbox(List.of(-74.0, -39.0, -73.0, -38.0));
        when(regionRepository.findAll()).thenReturn(List.of(region));

        List<HeatAlertEvent> result = router.resolveForRegion(regionId, since);

        // A region 50/50 split (1 covered comuna, 1 uncovered comuna) must SUM both
        // contributions, not decide all-or-nothing for the whole region (FIX 1, C1).
        assertEquals(2, result.size());
    }

    @Test
    void resolveForRegion_fullyCovered_centroidFallbackNeverInvoked() {
        String regionId = "region-covered";
        ComunaInfo comunaA = coveredComuna("comuna-A", regionId, -37.0, -72.0);
        when(comunaInfoRepository.findByRegionId(regionId)).thenReturn(List.of(comunaA));

        LocalDateTime since = LocalDateTime.now().minusHours(48);
        HeatAlertEvent geometricEvent = firmsEvent(regionId, "comuna-A", -37.0, -72.0, LocalDateTime.now().minusHours(10), 20.0);
        when(heatAlertEventRepository.findByComunaIdInAndFechaEventoAfter(eq(List.of("comuna-A")), eq(since)))
            .thenReturn(List.of(geometricEvent));

        List<HeatAlertEvent> result = router.resolveForRegion(regionId, since);

        assertEquals(1, result.size());
        verify(heatAlertEventRepository, never()).findByFuenteAndFechaEventoAfter(any(), any());
        verify(regionRepository, never()).findAll();
    }

    // ---- FIX 2 (finding C6): fallback candidate pool must NOT be pre-filtered by persisted regionId ----

    // THE cross-region dedup regression: two uncovered overlapping regions A and B; a
    // detection physically nearer B gets synced first by A's cron leg (Decision 2 dedup is
    // region-independent, so it persists exactly once under regionId=A). B's fallback must
    // still count it via centroid correction, not silently zero, because the candidate pool
    // is sourced by fuente+recency only — never gated by the event's persisted regionId.
    @Test
    void resolveForRegion_detectionPersistedByDifferentRegionsLeg_stillCountedByGeometricallyNearerRegion() {
        String regionA = "region-A";
        String regionB = "region-B";

        // Region B uncovered: no comunas seeded (forces whole-region fallback).
        when(comunaInfoRepository.findByRegionId(regionB)).thenReturn(List.of());

        Region a = new Region();
        a.setId(regionA);
        a.setAoiBbox(List.of(-74.0, -38.0, -73.0, -37.0)); // centroid (-73.5, -37.5)

        Region b = new Region();
        b.setId(regionB);
        b.setAoiBbox(List.of(-73.0, -36.0, -72.0, -35.0)); // centroid (-72.5, -35.5)

        when(regionRepository.findAll()).thenReturn(List.of(a, b));

        LocalDateTime since = LocalDateTime.now().minusHours(48);

        // Detection physically much closer to region B's centroid, but persisted with
        // regionId=region-A because A's cron leg happened to sync it first (Decision 2:
        // dedup is region-independent, so it exists exactly once, "owned" by whichever leg
        // won the race — a meaningless artifact for true geographic ownership).
        HeatAlertEvent detection = firmsEvent(regionA, null, -35.6, -72.6, LocalDateTime.now().minusHours(5), 25.0);
        when(heatAlertEventRepository.findByFuenteAndFechaEventoAfter("NASA_FIRMS", since)).thenReturn(List.of(detection));

        List<HeatAlertEvent> resultForB = router.resolveForRegion(regionB, since);

        // Region B's fallback must still see and claim this row via centroid distance —
        // NOT filtered out because its persisted regionId says "region-A".
        assertEquals(1, resultForB.size());
    }

    @Test
    void resolveForRegion_detectionPersistedByDifferentRegionsLeg_regionAFallbackDoesNotDoubleCount() {
        String regionA = "region-A";
        String regionB = "region-B";

        // Region A uncovered: no comunas seeded (forces whole-region fallback).
        when(comunaInfoRepository.findByRegionId(regionA)).thenReturn(List.of());

        Region a = new Region();
        a.setId(regionA);
        a.setAoiBbox(List.of(-74.0, -38.0, -73.0, -37.0));

        Region b = new Region();
        b.setId(regionB);
        b.setAoiBbox(List.of(-73.0, -36.0, -72.0, -35.0));

        when(regionRepository.findAll()).thenReturn(List.of(a, b));

        LocalDateTime since = LocalDateTime.now().minusHours(48);
        HeatAlertEvent detection = firmsEvent(regionA, null, -35.6, -72.6, LocalDateTime.now().minusHours(5), 25.0);
        when(heatAlertEventRepository.findByFuenteAndFechaEventoAfter("NASA_FIRMS", since)).thenReturn(List.of(detection));

        List<HeatAlertEvent> resultForA = router.resolveForRegion(regionA, since);

        // Region A's own fallback must NOT claim it (it is geometrically nearer B) —
        // confirms centroid distance, not persisted regionId, decides ownership both ways.
        assertTrue(resultForA.isEmpty());
    }

    // ---- Gap-vs-offshore distinction (Invariant 4) ----

    @Test
    void resolveForComuna_coveredComunaOffshoreRow_staysExcludedNotRoutedToFallback() {
        ComunaInfo comuna = coveredComuna("comuna-A", "region-X", -37.5, -72.5);
        LocalDateTime since = LocalDateTime.now().minusHours(48);

        when(heatAlertEventRepository.findByComunaIdAndFechaEventoAfter("comuna-A", since)).thenReturn(List.of());

        List<HeatAlertEvent> result = router.resolveForComuna(comuna, since);

        assertTrue(result.isEmpty());
        verify(heatAlertEventRepository, never()).findByFuenteAndFechaEventoAfter(any(), any());
    }
}
