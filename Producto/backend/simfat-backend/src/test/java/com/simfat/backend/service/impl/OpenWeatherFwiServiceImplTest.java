package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.model.TerritoryWeatherObservation;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import com.simfat.backend.service.fwi.ComunaFwiAdvanceResult;
import com.simfat.backend.service.fwi.ComunaFwiStateService;
import com.simfat.backend.service.fwi.FwiInputs;
import com.simfat.backend.service.fwi.FwiOutputs;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OpenWeatherFwiServiceImplTest {

    private static final ZoneId SANTIAGO_ZONE = ZoneId.of("America/Santiago");

    private MockWebServer server;

    @Mock
    private TerritoryWeatherObservationRepository weatherRepository;
    @Mock
    private RegionRepository regionRepository;
    @Mock
    private ComunaInfoRepository comunaInfoRepository;
    @Mock
    private ComunaFwiStateService comunaFwiStateService;

    private OpenWeatherFwiServiceImpl service;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        service = new OpenWeatherFwiServiceImpl(
            weatherRepository, regionRepository, new ObjectMapper(), comunaInfoRepository, comunaFwiStateService);
        ReflectionTestUtils.setField(service, "baseUrl", server.url("/").toString().replaceAll("/$", ""));
        ReflectionTestUtils.setField(service, "syncEnabled", true);
        ReflectionTestUtils.setField(service, "fwiMethod", "PROXY_V1");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void syncFwiByRegion_persistsAllFiveClimateFieldsIncludingSoilTemp() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{"
                + "\"daily\":{"
                + "\"temperature_2m_max\":[28.5],"
                + "\"relative_humidity_2m_min\":[35.0],"
                + "\"windspeed_10m_max\":[20.0],"
                + "\"precipitation_sum\":[0.0]"
                + "},"
                + "\"hourly\":{"
                + "\"soil_temperature_0cm\":[18.0,18.5,19.0,19.5,20.0,20.5,21.0,21.5,22.0,22.5,23.0,23.5,"
                + "23.0,22.5,22.0,21.5,21.0,20.5,20.0,19.5,19.0,18.5,18.0,17.5]"
                + "}"
                + "}"));

        boolean result = service.syncFwiByRegion("comuna-1", -38.0, -72.0);

        assertTrue(result);

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("hourly=soil_temperature_0cm"));

        ArgumentCaptor<TerritoryWeatherObservation> captor = ArgumentCaptor.forClass(TerritoryWeatherObservation.class);
        verify(weatherRepository).save(captor.capture());
        TerritoryWeatherObservation saved = captor.getValue();

        assertEquals(28.5, saved.getTempMax());
        assertEquals(35.0, saved.getHumidityMin());
        assertEquals(20.0, saved.getWindMax());
        assertEquals(0.0, saved.getPrecip());
        assertNotNull(saved.getSoilTemp());
        assertEquals(20.5, saved.getSoilTemp());
        assertNotNull(saved.getFwi());
    }

    @Test
    void syncFwiByRegion_requestsPastAndForecastHoursCappedAt24() throws InterruptedException {
        // Regression test: forecast_days=1 alone does NOT cap the hourly block
        // when past_hours is also set — Open-Meteo falls back to its default
        // ~16-day forecast horizon for "hourly", producing 408 points instead
        // of 48 and making the wind slider scrub weeks into the future.
        // forecast_hours=24 is the parameter that actually caps it.
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{"
                + "\"daily\":{"
                + "\"temperature_2m_max\":[28.5],"
                + "\"relative_humidity_2m_min\":[35.0],"
                + "\"windspeed_10m_max\":[20.0],"
                + "\"winddirection_10m_dominant\":[270.0],"
                + "\"precipitation_sum\":[0.0]"
                + "},"
                + "\"hourly\":{"
                + "\"time\":[],\"windspeed_10m\":[],\"winddirection_10m\":[]"
                + "}"
                + "}"));

        service.syncFwiByRegion("comuna-1", -38.0, -72.0);

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("past_hours=24"));
        assertTrue(request.getPath().contains("forecast_hours=24"));
    }

    @Test
    void syncFwiByRegion_persistsWindDirectionAndHourlySeries() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{"
                + "\"daily\":{"
                + "\"temperature_2m_max\":[28.5],"
                + "\"relative_humidity_2m_min\":[35.0],"
                + "\"windspeed_10m_max\":[20.0],"
                + "\"winddirection_10m_dominant\":[270.0],"
                + "\"precipitation_sum\":[0.0]"
                + "},"
                + "\"hourly\":{"
                + "\"time\":[\"2026-06-18T23:00\",\"2026-06-19T00:00\"],"
                + "\"windspeed_10m\":[5.3,6.1],"
                + "\"winddirection_10m\":[18.0,19.0]"
                + "}"
                + "}"));

        boolean result = service.syncFwiByRegion("comuna-1", -38.0, -72.0);
        assertTrue(result);

        ArgumentCaptor<TerritoryWeatherObservation> captor = ArgumentCaptor.forClass(TerritoryWeatherObservation.class);
        verify(weatherRepository).save(captor.capture());
        TerritoryWeatherObservation saved = captor.getValue();

        assertEquals(270.0, saved.getWindDirection());
        assertEquals(2, saved.getHourlyTimestamps().size());
        assertEquals(5.3, saved.getHourlyWindSpeed().get(0));
        assertEquals(19.0, saved.getHourlyWindDirection().get(1));
    }

    @Test
    void syncFwiByRegion_persistsTempMinAndCurrentHourWeatherCode() throws InterruptedException {
        // weather_code daily would be "the day's worst condition" — using the
        // hourly value closest to now avoids showing "rain" hours after it
        // stopped (same class of bug as the wind hourly window).
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String nowHourIso = now.withMinute(0).withSecond(0).withNano(0).toString();

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{"
                + "\"daily\":{"
                + "\"temperature_2m_max\":[28.5],"
                + "\"temperature_2m_min\":[12.0],"
                + "\"relative_humidity_2m_min\":[35.0],"
                + "\"windspeed_10m_max\":[20.0],"
                + "\"precipitation_sum\":[0.0]"
                + "},"
                + "\"hourly\":{"
                + "\"time\":[\"2020-01-01T00:00\",\"" + nowHourIso + "\"],"
                + "\"weather_code\":[0,61]"
                + "}"
                + "}"));

        boolean result = service.syncFwiByRegion("comuna-1", -38.0, -72.0);
        assertTrue(result);

        ArgumentCaptor<TerritoryWeatherObservation> captor = ArgumentCaptor.forClass(TerritoryWeatherObservation.class);
        verify(weatherRepository).save(captor.capture());
        TerritoryWeatherObservation saved = captor.getValue();

        assertEquals(12.0, saved.getTempMin());
        assertEquals(61, saved.getWeatherCode());
    }

    @Test
    void syncFwiByRegion_soilTempMissing_persistsNullWithoutFailingSync() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{"
                + "\"daily\":{"
                + "\"temperature_2m_max\":[28.5],"
                + "\"relative_humidity_2m_min\":[35.0],"
                + "\"windspeed_10m_max\":[20.0],"
                + "\"precipitation_sum\":[0.0]"
                + "},"
                + "\"hourly\":{}"
                + "}"));

        boolean result = service.syncFwiByRegion("comuna-2", -38.0, -72.0);

        assertTrue(result);

        ArgumentCaptor<TerritoryWeatherObservation> captor = ArgumentCaptor.forClass(TerritoryWeatherObservation.class);
        verify(weatherRepository).save(captor.capture());
        TerritoryWeatherObservation saved = captor.getValue();

        assertNull(saved.getSoilTemp());
        assertEquals(28.5, saved.getTempMax());
        assertNotNull(saved.getFwi());
    }

    @Test
    void syncFwiByRegion_defaultMethod_stampsFwiMethodProxyV1() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{"
                + "\"daily\":{"
                + "\"temperature_2m_max\":[28.5],"
                + "\"relative_humidity_2m_min\":[35.0],"
                + "\"windspeed_10m_max\":[20.0],"
                + "\"precipitation_sum\":[0.0]"
                + "},"
                + "\"hourly\":{}"
                + "}"));

        boolean result = service.syncFwiByRegion("comuna-1", -38.0, -72.0);
        assertTrue(result);

        ArgumentCaptor<TerritoryWeatherObservation> captor = ArgumentCaptor.forClass(TerritoryWeatherObservation.class);
        verify(weatherRepository).save(captor.capture());
        TerritoryWeatherObservation saved = captor.getValue();

        // territory.fwi.method defaults to PROXY_V1: production behavior is unchanged by
        // this slice, but every saved observation now stamps which method produced it
        // (design D4: legacy documents with fwiMethod == null are treated as PROXY_V1).
        assertEquals("PROXY_V1", saved.getFwiMethod());
        verify(comunaFwiStateService, never()).advance(any(), any(), any());
    }

    @Test
    void syncFwiByRegion_vanWagnerMethodForMonitoredComuna_derivesNoonInputsAndPersistsRealFwiChain()
            throws InterruptedException {
        ReflectionTestUtils.setField(service, "fwiMethod", "VAN_WAGNER");
        when(comunaInfoRepository.existsById("comuna-1")).thenReturn(true);

        LocalDate today = LocalDate.now(SANTIAGO_ZONE);
        String noonIso = today + "T12:00";
        FwiOutputs outputs = new FwiOutputs(80.1, 12.3, 45.6, 7.8, 20.1, 15.42, 0.99);
        when(comunaFwiStateService.advance(eq("comuna-1"), eq(today), any(FwiInputs.class)))
            .thenReturn(new ComunaFwiAdvanceResult(outputs, null));

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{"
                + "\"daily\":{"
                + "\"temperature_2m_max\":[28.5],"
                + "\"relative_humidity_2m_min\":[35.0],"
                + "\"windspeed_10m_max\":[20.0],"
                + "\"precipitation_sum\":[2.0]"
                + "},"
                + "\"hourly\":{"
                + "\"time\":[\"" + noonIso + "\"],"
                + "\"temperature_2m\":[24.0],"
                + "\"relative_humidity_2m\":[40.0],"
                + "\"wind_speed_10m\":[10.0],"
                + "\"precipitation\":[0.0]"
                + "}"
                + "}"));

        boolean result = service.syncFwiByRegion("comuna-1", -38.0, -72.0);
        assertTrue(result);

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("hourly=soil_temperature_0cm"));
        assertTrue(request.getPath().contains("temperature_2m"));
        assertTrue(request.getPath().contains("relative_humidity_2m"));
        assertTrue(request.getPath().contains("wind_speed_10m"));
        assertTrue(request.getPath().contains("precipitation"));

        ArgumentCaptor<FwiInputs> inputsCaptor = ArgumentCaptor.forClass(FwiInputs.class);
        verify(comunaFwiStateService).advance(eq("comuna-1"), eq(today), inputsCaptor.capture());
        FwiInputs capturedInputs = inputsCaptor.getValue();
        assertEquals(24.0, capturedInputs.tempC());
        assertEquals(40.0, capturedInputs.rhPct());
        assertEquals(10.0, capturedInputs.windKmh());
        // Documented simplification: precipMm uses the already-fetched daily
        // precipitation_sum (calendar-day total), not a strict 24h-ending-at-noon window.
        assertEquals(2.0, capturedInputs.precipMm());
        assertEquals(today.getMonthValue(), capturedInputs.month());

        ArgumentCaptor<TerritoryWeatherObservation> captor = ArgumentCaptor.forClass(TerritoryWeatherObservation.class);
        verify(weatherRepository).save(captor.capture());
        TerritoryWeatherObservation saved = captor.getValue();
        assertEquals("VAN_WAGNER", saved.getFwiMethod());
        assertEquals(15.42, saved.getFwi());
        assertEquals(80.1, saved.getFfmc());
        assertEquals(12.3, saved.getDmc());
        assertEquals(45.6, saved.getDc());
        assertEquals(7.8, saved.getIsi());
        assertEquals(20.1, saved.getBui());
        assertEquals(0.99, saved.getDsr());
    }

    @Test
    void syncFwiByRegion_vanWagnerMethodForNonComunaRegion_staysOnProxyAndNeverCallsVanWagnerChain()
            throws InterruptedException {
        // Gate mechanism (task 1d2.3 / CFW-8a): syncFwiForAllRegions() and the admin
        // /territory/sync endpoint both call this same method with a Region entity id
        // (e.g. "valparaiso", one of the 16 non-target display-only regions), which is
        // NOT a key in comuna_info -- so even with the flag flipped on, this must never
        // advance the comuna_fwi_state chain for a non-comuna id.
        ReflectionTestUtils.setField(service, "fwiMethod", "VAN_WAGNER");
        when(comunaInfoRepository.existsById("valparaiso")).thenReturn(false);

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{"
                + "\"daily\":{"
                + "\"temperature_2m_max\":[28.5],"
                + "\"relative_humidity_2m_min\":[35.0],"
                + "\"windspeed_10m_max\":[20.0],"
                + "\"precipitation_sum\":[0.0]"
                + "},"
                + "\"hourly\":{}"
                + "}"));

        boolean result = service.syncFwiByRegion("valparaiso", -33.0, -71.6);
        assertTrue(result);

        verify(comunaFwiStateService, never()).advance(any(), any(), any());
        ArgumentCaptor<TerritoryWeatherObservation> captor = ArgumentCaptor.forClass(TerritoryWeatherObservation.class);
        verify(weatherRepository).save(captor.capture());
        assertEquals("PROXY_V1", captor.getValue().getFwiMethod());
    }

    @Test
    void syncFwiByRegion_vanWagnerMethodMissingNoonHourlyData_fallsBackToProxyWithoutFailingSync()
            throws InterruptedException {
        ReflectionTestUtils.setField(service, "fwiMethod", "VAN_WAGNER");
        when(comunaInfoRepository.existsById("comuna-1")).thenReturn(true);

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{"
                + "\"daily\":{"
                + "\"temperature_2m_max\":[28.5],"
                + "\"relative_humidity_2m_min\":[35.0],"
                + "\"windspeed_10m_max\":[20.0],"
                + "\"precipitation_sum\":[0.0]"
                + "},"
                + "\"hourly\":{}"
                + "}"));

        boolean result = service.syncFwiByRegion("comuna-1", -38.0, -72.0);
        assertTrue(result);

        verify(comunaFwiStateService, never()).advance(any(), any(), any());
        ArgumentCaptor<TerritoryWeatherObservation> captor = ArgumentCaptor.forClass(TerritoryWeatherObservation.class);
        verify(weatherRepository).save(captor.capture());
        assertEquals("PROXY_V1", captor.getValue().getFwiMethod());
    }
}
