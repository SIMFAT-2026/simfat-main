package com.simfat.backend.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.simfat.backend.dto.DataFreshnessDTO;
import com.simfat.backend.dto.DataFreshnessStatus;
import com.simfat.backend.dto.IndicatorLatestDTO;
import com.simfat.backend.dto.IndicatorMapResponseDTO;
import com.simfat.backend.dto.IndicatorSeriesDTO;
import com.simfat.backend.dto.IndicatorSeriesPointDTO;
import com.simfat.backend.dto.SyncRunResponseDTO;
import com.simfat.backend.service.DashboardIndicatorService;
import com.simfat.backend.service.DashboardService;
import com.simfat.backend.service.OpenEoSyncService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;
    @MockBean
    private DashboardIndicatorService dashboardIndicatorService;
    @MockBean
    private OpenEoSyncService openEoSyncService;

    @Test
    void getLatestIndicator_returnsApiResponseWrappedPayload() throws Exception {
        IndicatorLatestDTO latest = new IndicatorLatestDTO();
        latest.setRegionId("region-1");
        latest.setIndicator("NDVI");
        latest.setValue(0.67);
        latest.setObservedAt(LocalDateTime.now());
        latest.setCached(true);
        when(dashboardIndicatorService.getLatest(eq("region-1"), any())).thenReturn(latest);

        mockMvc.perform(get("/api/dashboard/indicators/latest")
                .param("regionId", "region-1")
                .param("indicator", "NDVI"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.regionId", is("region-1")))
            .andExpect(jsonPath("$.data.indicator", is("NDVI")))
            .andExpect(jsonPath("$.data.cached", is(true)));
    }

    @Test
    void runSync_returnsApiResponseWrappedPayload() throws Exception {
        SyncRunResponseDTO syncRunResponse = new SyncRunResponseDTO();
        syncRunResponse.setTotalRegions(1);
        syncRunResponse.setTotalJobsAccepted(2);
        when(openEoSyncService.runSync("region-1", null, null)).thenReturn(syncRunResponse);

        mockMvc.perform(post("/api/dashboard/sync/run").param("regionId", "region-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.totalRegions", is(1)))
            .andExpect(jsonPath("$.data.totalJobsAccepted", is(2)));
    }

    @Test
    void getSeriesMapAndFreshness_areExposed() throws Exception {
        IndicatorSeriesDTO series = new IndicatorSeriesDTO();
        series.setRegionId("region-1");
        series.setIndicator("NDMI");
        series.setGranularity("week");
        IndicatorSeriesPointDTO point = new IndicatorSeriesPointDTO();
        point.setTs(LocalDateTime.parse("2026-01-06T00:00:00"));
        point.setValue(0.52);
        series.setPoints(List.of(point));
        when(dashboardIndicatorService.getSeries(eq("region-1"), any(), eq("2026-01-01"), eq("2026-01-31"), eq("week")))
            .thenReturn(series);

        IndicatorMapResponseDTO map = new IndicatorMapResponseDTO();
        map.setIndicator("NDMI");
        map.setItems(List.of());
        when(dashboardIndicatorService.getMap(any(), eq("2026-01-01"), eq("2026-01-31"), eq(100))).thenReturn(map);

        DataFreshnessDTO freshness = new DataFreshnessDTO();
        freshness.setRegionId("region-1");
        freshness.setStale(false);
        freshness.setStatus(DataFreshnessStatus.FRESH);
        when(dashboardIndicatorService.getDataFreshness("region-1")).thenReturn(freshness);

        mockMvc.perform(get("/api/dashboard/indicators/series")
                .param("regionId", "region-1")
                .param("indicator", "NDMI")
                .param("from", "2026-01-01")
                .param("to", "2026-01-31")
                .param("granularity", "week"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.granularity", is("week")))
            .andExpect(jsonPath("$.data.points[0].ts", is("2026-01-06T00:00:00")));

        mockMvc.perform(get("/api/dashboard/indicators/map")
                .param("indicator", "NDMI")
                .param("from", "2026-01-01")
                .param("to", "2026-01-31")
                .param("limit", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.indicator", is("NDMI")));

        mockMvc.perform(get("/api/dashboard/data-freshness").param("regionId", "region-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.regionId", is("region-1")))
            .andExpect(jsonPath("$.data.status", is("FRESH")));
    }
}
