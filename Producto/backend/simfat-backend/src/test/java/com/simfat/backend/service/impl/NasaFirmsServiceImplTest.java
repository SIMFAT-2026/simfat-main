package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.RegionRepository;
import java.io.IOException;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NasaFirmsServiceImplTest {

    private MockWebServer server;

    @Mock
    private HeatAlertEventRepository heatAlertEventRepository;
    @Mock
    private RegionRepository regionRepository;

    // Mocked with CALLS_REAL_METHODS: ComunaInfoRepository#findOneByGeometryIntersects is
    // a default method that delegates to findByGeometryIntersects — a plain @Mock would
    // not invoke the real default-method body, it would return Mockito's mock default
    // (null/empty) regardless of how findByGeometryIntersects is stubbed.
    private ComunaInfoRepository comunaInfoRepository;

    private NasaFirmsServiceImpl service;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        comunaInfoRepository = mock(ComunaInfoRepository.class, CALLS_REAL_METHODS);
        service = new NasaFirmsServiceImpl(heatAlertEventRepository, regionRepository, comunaInfoRepository);
        ReflectionTestUtils.setField(service, "mapKey", "test-key");
        ReflectionTestUtils.setField(service, "baseUrl", server.url("/api").toString().replaceAll("/$", ""));
        ReflectionTestUtils.setField(service, "firmsSource", "VIIRS_NOAA20_NRT");
        ReflectionTestUtils.setField(service, "dayRange", 2);
        ReflectionTestUtils.setField(service, "syncEnabled", true);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private static final String CSV_HEADER = "latitude,longitude,confidence,frp,satellite,acq_date,acq_time";

    @Test
    void parseCsvResponse_pointInsideSeededComuna_savedEventHasThatComunaId() throws Exception {
        String csv = CSV_HEADER + "\n-37.5,-72.5,h,42.0,N20,2026-06-29,0130\n";
        server.enqueue(new MockResponse().setBody(csv).setResponseCode(200));

        when(heatAlertEventRepository.existsByLatitudAndLongitudAndFechaEventoAndFuente(
            any(), any(), any(), any())).thenReturn(false);

        ComunaInfo comunaA = new ComunaInfo();
        comunaA.setId("comuna-A");
        when(comunaInfoRepository.findByGeometryIntersects(-72.5, -37.5))
            .thenReturn(List.of(comunaA));

        int saved = service.syncActiveFiresByRegion("biobio", -73.0, -38.0, -72.0, -37.0);

        assertEquals(1, saved);
        ArgumentCaptor<List<HeatAlertEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(heatAlertEventRepository).saveAll(captor.capture());
        HeatAlertEvent savedEvent = captor.getValue().get(0);
        assertEquals("comuna-A", savedEvent.getComunaId());
        assertEquals("biobio", savedEvent.getRegionId());
    }

    @Test
    void parseCsvResponse_offshoreCsvRow_savedEventHasNullComunaIdNotDropped() throws Exception {
        String csv = CSV_HEADER + "\n-1.0,-1.0,h,10.0,N20,2026-06-29,0200\n";
        server.enqueue(new MockResponse().setBody(csv).setResponseCode(200));

        when(heatAlertEventRepository.existsByLatitudAndLongitudAndFechaEventoAndFuente(
            any(), any(), any(), any())).thenReturn(false);
        when(comunaInfoRepository.findByGeometryIntersects(anyDouble(), anyDouble()))
            .thenReturn(List.of());

        int saved = service.syncActiveFiresByRegion("biobio", -73.0, -38.0, -72.0, -37.0);

        assertEquals(1, saved);
        ArgumentCaptor<List<HeatAlertEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(heatAlertEventRepository).saveAll(captor.capture());
        HeatAlertEvent savedEvent = captor.getValue().get(0);
        assertNotNull(savedEvent);
        assertNull(savedEvent.getComunaId());
    }

    @Test
    void parseCsvResponse_sameDetectionSeenOnSecondRegionLeg_dedupedNoSecondInsertNoAttributionLookup() throws Exception {
        String csv = CSV_HEADER + "\n-37.5,-72.5,h,42.0,N20,2026-06-29,0130\n";
        server.enqueue(new MockResponse().setBody(csv).setResponseCode(200));

        // Already persisted by a previous region leg -> identity dedup short-circuits
        when(heatAlertEventRepository.existsByLatitudAndLongitudAndFechaEventoAndFuente(
            any(), any(), any(), any())).thenReturn(true);

        int saved = service.syncActiveFiresByRegion("nuble", -73.0, -38.0, -72.0, -37.0);

        assertEquals(0, saved);
        verify(heatAlertEventRepository).saveAll(List.of());
        verify(comunaInfoRepository, never()).findByGeometryIntersects(anyDouble(), anyDouble());
    }
}
