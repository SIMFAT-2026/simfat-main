package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.model.TerritoryWeatherObservation;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import java.io.IOException;
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

    private MockWebServer server;

    @Mock
    private TerritoryWeatherObservationRepository weatherRepository;
    @Mock
    private RegionRepository regionRepository;

    private OpenWeatherFwiServiceImpl service;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        service = new OpenWeatherFwiServiceImpl(weatherRepository, regionRepository, new ObjectMapper());
        ReflectionTestUtils.setField(service, "baseUrl", server.url("/").toString().replaceAll("/$", ""));
        ReflectionTestUtils.setField(service, "syncEnabled", true);
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
}
