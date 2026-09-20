package com.simfat.backend.controller;

import com.simfat.backend.repository.CitizenReportRepository;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.ForestLossRecordRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import com.simfat.backend.service.ComunaRiskService;
import com.simfat.backend.service.NasaFirmsService;
import com.simfat.backend.service.OpenEoIngestService;
import com.simfat.backend.service.OpenWeatherFwiService;
import com.simfat.backend.service.TerritoryRiskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-context MockMvc base that replaces every Mongo-backed collaborator with a mock, so
 * controller tests run without a database (same set as SecurityDefaultDenyContractTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class MongoFreeWebTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @MockBean
    protected HeatAlertEventRepository heatAlertEventRepository;
    @MockBean
    protected CitizenReportRepository citizenReportRepository;
    @MockBean
    protected ForestLossRecordRepository forestLossRecordRepository;
    @MockBean
    protected OpenEoIndicatorObservationRepository openEoIndicatorObservationRepository;
    @MockBean
    protected RegionRepository regionRepository;
    @MockBean
    protected ComunaInfoRepository comunaInfoRepository;
    @MockBean
    protected TerritoryWeatherObservationRepository territoryWeatherObservationRepository;
    @MockBean
    protected TerritoryRiskService territoryRiskService;
    @MockBean
    protected NasaFirmsService nasaFirmsService;
    @MockBean
    protected OpenWeatherFwiService openWeatherFwiService;
    @MockBean
    protected ComunaRiskService comunaRiskService;
    @MockBean
    protected OpenEoIngestService openEoIngestService;
}
