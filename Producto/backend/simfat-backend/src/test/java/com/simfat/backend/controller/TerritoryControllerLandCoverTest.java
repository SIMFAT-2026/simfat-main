package com.simfat.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaMapbiomasStats;
import com.simfat.backend.repository.CitizenReportRepository;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.ComunaMapbiomasStatsRepository;
import com.simfat.backend.repository.ForestLossRecordRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import com.simfat.backend.service.ComunaRiskService;
import com.simfat.backend.service.NasaFirmsService;
import com.simfat.backend.service.OpenWeatherFwiService;
import com.simfat.backend.service.TerritoryRiskService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * LANDCOVER wiring in {@link TerritoryController} (slice S3-0), exercised WITHOUT Spring or Mongo:
 * the controller is constructed directly with mocked repositories. The Spring-wired MockMvc
 * equivalents live in {@code TerritoryControllerMapbiomasYearTest} (they need Mongo).
 */
class TerritoryControllerLandCoverTest {

    private ComunaInfoRepository comunaInfoRepository;
    private ComunaMapbiomasStatsRepository statsRepository;
    private TerritoryController controller;

    @BeforeEach
    void setUp() {
        comunaInfoRepository = mock(ComunaInfoRepository.class);
        statsRepository = mock(ComunaMapbiomasStatsRepository.class);
        controller = new TerritoryController(
            mock(HeatAlertEventRepository.class), mock(CitizenReportRepository.class),
            mock(ForestLossRecordRepository.class), mock(OpenEoIndicatorObservationRepository.class),
            mock(RegionRepository.class), mock(TerritoryRiskService.class), mock(NasaFirmsService.class),
            mock(OpenWeatherFwiService.class), mock(ComunaRiskService.class), comunaInfoRepository,
            mock(TerritoryWeatherObservationRepository.class), statsRepository);
        controller.setMapbiomasDataVersion("test-version");

        ComunaInfo comuna = new ComunaInfo();
        comuna.setId("c1");
        comuna.setRegionId("biobio");
        when(comunaInfoRepository.findByRegionId("biobio")).thenReturn(List.of(comuna));
    }

    /** 1999-2024 series (26 values): class 59 all years; class 12 dominant only in 2024. */
    private static ComunaMapbiomasStats stats() {
        int[] forest = new int[26];
        int[] grass = new int[26];
        for (int i = 0; i < 26; i++) {
            forest[i] = 6000;
            grass[i] = i == 25 ? 7000 : 4000;
        }
        ComunaMapbiomasStats.SharesByYear series = new ComunaMapbiomasStats.SharesByYear();
        series.setFirstYear(1999);
        series.setLastYear(2024);
        series.setUnit("bp");
        series.setClasses(Map.of("59", forest, "12", grass));
        ComunaMapbiomasStats.LandCover landCover = new ComunaMapbiomasStats.LandCover();
        landCover.setSharesByYear(series);
        ComunaMapbiomasStats s = new ComunaMapbiomasStats();
        s.setComunaId("c1");
        s.setLandCover(landCover);
        return s;
    }

    private void givenStats() {
        when(statsRepository.findByRegionIdAndDataVersion("biobio", "test-version")).thenReturn(List.of(stats()));
        when(statsRepository.findByDataVersion("test-version")).thenReturn(List.of(stats()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> landCover(String indicators, String year, boolean publicRoute) {
        var response = publicRoute
            ? controller.getPublicLayers("biobio", indicators, null, null, year)
            : controller.getLayers("biobio", indicators, null, null, year);
        return (Map<String, Object>) response.getBody().getData().getLayers().get("LANDCOVER");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> entry(Map<String, Object> layer) {
        return ((Map<String, Map<String, Object>>) layer.get("values")).get("c1");
    }

    @Test
    void layers_landcoverWithYear_returnsThatYearsValueMap() {
        givenStats();

        Map<String, Object> layer = landCover("LANDCOVER", "2000", false);

        assertThat(layer.get("indicator")).isEqualTo("LANDCOVER");
        assertThat(layer.get("unit")).isEqualTo("fraction");
        assertThat(layer.get("year")).isEqualTo(2000);
        assertThat(layer.get("minYear")).isEqualTo(1999);
        assertThat(layer.get("maxYear")).isEqualTo(2024);
        assertThat(layer.get("dataVersion")).isEqualTo("test-version");
        assertThat(entry(layer).get("dominantClass")).isEqualTo(59);
    }

    @Test
    void publicLayers_landcoverServedWithTheSameShape() {
        givenStats();

        assertThat(landCover("LANDCOVER", "2024", true))
            .isEqualTo(landCover("LANDCOVER", "2024", false));
        assertThat(entry(landCover("LANDCOVER", "2024", true)).get("dominantClass")).isEqualTo(12);
    }

    @Test
    void omittedYear_defaultsToTheLatestYearWithLandCoverData() {
        givenStats();

        Map<String, Object> layer = landCover("LANDCOVER", null, true);

        assertThat(layer.get("year")).isEqualTo(2024);
        assertThat(entry(layer).get("dominantClass")).isEqualTo(12);
    }

    @Test
    void yearOutOfRange_isRejected_andNeverTouchesMongoOrTheCache() {
        for (String bad : new String[] {"1998", "2025", "abc", "", "2020.5"}) {
            assertThatThrownBy(() -> landCover("LANDCOVER", bad, true))
                .as("year=%s", bad)
                .isInstanceOf(BadRequestException.class);
        }
        verify(statsRepository, never()).findByRegionIdAndDataVersion(anyString(), anyString());
        verify(statsRepository, never()).findByDataVersion(anyString());
    }

    @Test
    void landcoverAndBurnedTogether_yearIsValidatedAgainstEach_andNamesLandcover() {
        // 2025 is valid for BURNED (2013-2025) but not for LANDCOVER (1999-2024).
        assertThatThrownBy(() -> landCover("LANDCOVER,BURNED", "2025", true))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("LANDCOVER");
    }

    @Test
    void secondIdenticalRequest_isServedFromTheCache_oneBoundedReadByRegionAndVersion() {
        givenStats();

        landCover("LANDCOVER", "2010", true);
        landCover("LANDCOVER", "2010", true);

        verify(statsRepository, times(1)).findByRegionIdAndDataVersion("biobio", "test-version");
        verify(statsRepository, never()).findAll();
    }

    @Test
    void garbageRegion_normalizesToACanonicalRegion_soTheCacheKeySpaceStaysClosed() {
        givenStats();

        controller.getPublicLayers("garbage-region-1", "LANDCOVER", null, null, "2010");
        controller.getPublicLayers("garbage-region-2", "LANDCOVER", null, null, "2010");

        // Both normalize to the default region: one read, one cache entry.
        verify(statsRepository, times(1)).findByRegionIdAndDataVersion(anyString(), any());
    }
}
