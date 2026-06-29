package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.RegionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.test.util.ReflectionTestUtils;

// Layer C (design.md Decision 5): sync-time attribution and the new
// region-independent dedup key, against spec scenarios under "Sync-time
// point-in-polygon attribution" and Decision 2 (dedup key rethink).
@ExtendWith(MockitoExtension.class)
class NasaFirmsServiceImplTest {

    private static final String CSV_HEADER =
        "latitude,longitude,confidence,frp,satellite,acq_date,acq_time";

    @Mock
    private HeatAlertEventRepository heatAlertEventRepository;
    @Mock
    private RegionRepository regionRepository;
    @Mock
    private ComunaInfoRepository comunaInfoRepository;

    private NasaFirmsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NasaFirmsServiceImpl(heatAlertEventRepository, regionRepository, comunaInfoRepository);
    }

    @Test
    void parseCsvResponse_pointInsideSeededComuna_savedEventHasThatComunaId() {
        ComunaInfo comunaA = new ComunaInfo();
        comunaA.setId("comuna-a");
        when(comunaInfoRepository.findOneByGeometryIntersects(any(GeoJsonPoint.class)))
            .thenReturn(Optional.of(comunaA));
        when(heatAlertEventRepository.existsByLatitudAndLongitudAndFechaEventoAndFuente(
            anyDouble(), anyDouble(), any(), any()))
            .thenReturn(false);

        String csv = CSV_HEADER + "\n-37.45,-73.40,h,80.0,N20,2026-06-28,1200\n";

        List<HeatAlertEvent> result = invokeParseCsvResponse(csv, "biobio");

        assertEquals(1, result.size());
        assertEquals("comuna-a", result.get(0).getComunaId());
    }

    @Test
    void parseCsvResponse_offshorePoint_savedEventHasNullComunaId_notDropped() {
        when(comunaInfoRepository.findOneByGeometryIntersects(any(GeoJsonPoint.class)))
            .thenReturn(Optional.empty());
        when(heatAlertEventRepository.existsByLatitudAndLongitudAndFechaEventoAndFuente(
            anyDouble(), anyDouble(), any(), any()))
            .thenReturn(false);

        String csv = CSV_HEADER + "\n-40.0,-90.0,h,80.0,N20,2026-06-28,1200\n";

        List<HeatAlertEvent> result = invokeParseCsvResponse(csv, "biobio");

        assertEquals(1, result.size());
        assertNull(result.get(0).getComunaId());
    }

    @Test
    void parseCsvResponse_sameDetectionSeenOnSecondRegionLeg_dedupedByRegionIndependentKey() {
        when(heatAlertEventRepository.existsByLatitudAndLongitudAndFechaEventoAndFuente(
            anyDouble(), anyDouble(), any(), any()))
            .thenReturn(true);

        String csv = CSV_HEADER + "\n-37.45,-73.40,h,80.0,N20,2026-06-28,1200\n";

        List<HeatAlertEvent> result = invokeParseCsvResponse(csv, "nuble");

        assertEquals(0, result.size());
        verify(heatAlertEventRepository, never()).saveAll(any());
        verify(comunaInfoRepository, never()).findOneByGeometryIntersects(any());
    }

    @SuppressWarnings("unchecked")
    private List<HeatAlertEvent> invokeParseCsvResponse(String csvBody, String regionId) {
        return (List<HeatAlertEvent>) ReflectionTestUtils.invokeMethod(
            service, "parseCsvResponse", csvBody, regionId);
    }
}
