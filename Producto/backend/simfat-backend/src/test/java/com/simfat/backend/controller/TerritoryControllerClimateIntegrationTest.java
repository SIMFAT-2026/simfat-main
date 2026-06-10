package com.simfat.backend.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaRiskSnapshot;
import com.simfat.backend.model.TerritoryWeatherObservation;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import com.simfat.backend.service.ComunaRiskService;
import com.simfat.backend.service.TerritoryRiskService;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TerritoryControllerClimateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ComunaInfoRepository comunaInfoRepository;

    @Autowired
    private TerritoryWeatherObservationRepository weatherObservationRepository;

    @MockBean
    private TerritoryRiskService territoryRiskService;

    @MockBean
    private ComunaRiskService comunaRiskService;

    @BeforeEach
    void setUp() {
        comunaInfoRepository.deleteAll();
        weatherObservationRepository.deleteAll();

        ComunaInfo withData = new ComunaInfo();
        withData.setId("comuna-with-data");
        withData.setNombre("Con Datos");
        withData.setRegionId("biobio");
        withData.setCenterLat(-37.0);
        withData.setCenterLon(-72.0);
        comunaInfoRepository.save(withData);

        ComunaInfo withoutData = new ComunaInfo();
        withoutData.setId("comuna-without-data");
        withoutData.setNombre("Sin Datos");
        withoutData.setRegionId("biobio");
        withoutData.setCenterLat(-37.5);
        withoutData.setCenterLon(-72.5);
        comunaInfoRepository.save(withoutData);

        TerritoryWeatherObservation obs = new TerritoryWeatherObservation();
        obs.setRegionId("comuna-with-data");
        obs.setObservedAt(LocalDateTime.now());
        obs.setSource("open-meteo");
        obs.setFwi(25.0);
        obs.setTempMax(28.5);
        obs.setHumidityMin(35.0);
        obs.setWindMax(20.0);
        obs.setPrecip(0.0);
        obs.setSoilTemp(19.5);
        obs.setIngestedAt(LocalDateTime.now());
        weatherObservationRepository.save(obs);
    }

    @AfterEach
    void tearDown() {
        comunaInfoRepository.deleteAll();
        weatherObservationRepository.deleteAll();
    }

    @Test
    @WithMockUser(authorities = "ROLE_VERIFIED_USER")
    void getLayers_windHumidityAirTempSoilTemp_returnValueMapsWithUnitsAndOmitMissingComunas() throws Exception {
        mockMvc.perform(get("/api/territory/layers")
                .param("regionId", "biobio")
                .param("indicators", "WIND,HUMIDITY,AIR_TEMP,SOIL_TEMP"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.layers.WIND.unit", is("km/h")))
            .andExpect(jsonPath("$.data.layers.WIND.values.['comuna-with-data'].value", is(20.0)))
            .andExpect(jsonPath("$.data.layers.WIND.values.['comuna-without-data']").doesNotExist())
            .andExpect(jsonPath("$.data.layers.HUMIDITY.unit", is("%")))
            .andExpect(jsonPath("$.data.layers.HUMIDITY.values.['comuna-with-data'].value", is(35.0)))
            .andExpect(jsonPath("$.data.layers.AIR_TEMP.unit", is("°C")))
            .andExpect(jsonPath("$.data.layers.AIR_TEMP.values.['comuna-with-data'].value", is(28.5)))
            .andExpect(jsonPath("$.data.layers.SOIL_TEMP.unit", is("°C")))
            .andExpect(jsonPath("$.data.layers.SOIL_TEMP.values.['comuna-with-data'].value", is(19.5)));
    }

    @Test
    @WithMockUser(authorities = "ROLE_VERIFIED_USER")
    void getLayers_invalidIndicator_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/territory/layers")
                .param("regionId", "biobio")
                .param("indicators", "NOT_A_REAL_INDICATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.layers", is(Map.of())));
    }

    @Test
    @WithMockUser(authorities = "ROLE_VERIFIED_USER")
    void getComunalRiskScores_includesFwiInputsWithNullableComponents() throws Exception {
        ComunaRiskSnapshot snapshot = new ComunaRiskSnapshot();
        snapshot.setComunaId("comuna-with-data");
        snapshot.setRegionId("biobio");
        snapshot.setNombreComuna("Con Datos");
        snapshot.setAlertLevel("NORMAL");
        snapshot.setScoreComposite(0.1);
        snapshot.setMode("STANDARD");

        ComunaRiskSnapshot snapshotNoData = new ComunaRiskSnapshot();
        snapshotNoData.setComunaId("comuna-without-data");
        snapshotNoData.setRegionId("biobio");
        snapshotNoData.setNombreComuna("Sin Datos");
        snapshotNoData.setAlertLevel("NORMAL");
        snapshotNoData.setScoreComposite(0.0);
        snapshotNoData.setMode("STANDARD");

        when(comunaRiskService.getLatestSnapshotsByRegion(any())).thenReturn(Map.of(
            "comuna-with-data", snapshot,
            "comuna-without-data", snapshotNoData
        ));

        mockMvc.perform(get("/api/territory/risk-score/comunas/biobio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.['comuna-with-data'].fwiInputs.tempMax", is(28.5)))
            .andExpect(jsonPath("$.data.['comuna-with-data'].fwiInputs.humidityMin", is(35.0)))
            .andExpect(jsonPath("$.data.['comuna-with-data'].fwiInputs.windMax", is(20.0)))
            .andExpect(jsonPath("$.data.['comuna-with-data'].fwiInputs.precip", is(0.0)))
            .andExpect(jsonPath("$.data.['comuna-without-data'].fwiInputs", is(notNullValue())))
            .andExpect(jsonPath("$.data.['comuna-without-data'].fwiInputs.tempMax").doesNotExist());
    }
}
