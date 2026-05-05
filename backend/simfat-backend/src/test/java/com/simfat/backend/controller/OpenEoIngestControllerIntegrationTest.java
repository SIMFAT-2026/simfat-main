package com.simfat.backend.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.simfat.backend.dto.OpenEoMeasurementIngestResponseDTO;
import com.simfat.backend.service.OpenEoIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "openeo.ingest.auth-token=test-ingest-token")
class OpenEoIngestControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpenEoIngestService openEoIngestService;

    @Test
    void ingestMeasurement_returnsCreated_whenTokenIsValid() throws Exception {
        OpenEoMeasurementIngestResponseDTO response = new OpenEoMeasurementIngestResponseDTO();
        response.setSynced(true);
        response.setStatus("finished");
        response.setJobId("job-123");
        response.setObservationPersisted(true);
        when(openEoIngestService.ingestMeasurement(any())).thenReturn(response);

        mockMvc.perform(post("/api/indicators/measurements")
                .header("Authorization", "Bearer test-ingest-token")
                .contentType("application/json")
                .content(
                    """
                    {
                      "regionId": "region-1",
                      "indicatorType": "NDVI",
                      "periodStart": "2026-04-01",
                      "periodEnd": "2026-04-10",
                      "fetchedAt": "2026-04-15T10:00:00Z",
                      "measuredAt": "2026-04-15T10:00:00Z",
                      "value": 0.42
                    }
                    """
                ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.synced", is(true)))
            .andExpect(jsonPath("$.data.jobId", is("job-123")));
    }

    @Test
    void ingestMeasurement_returnsUnauthorized_whenTokenIsMissing() throws Exception {
        mockMvc.perform(post("/api/indicators/measurements")
                .contentType("application/json")
                .content(
                    """
                    {
                      "regionId": "region-1",
                      "indicatorType": "NDVI",
                      "periodStart": "2026-04-01",
                      "periodEnd": "2026-04-10"
                    }
                    """
                ))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success", is(false)));
    }
}
