package com.simfat.backend.integration.openeo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.exception.OpenEoClientException;
import com.simfat.backend.model.IndicatorType;
import java.io.IOException;
import java.time.LocalDateTime;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenEoServiceClientImplTest {

    private MockWebServer server;
    private OpenEoServiceClientImpl client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        RestClient restClient = RestClient.builder()
            .baseUrl(server.url("/").toString())
            .build();
        client = new OpenEoServiceClientImpl(restClient, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void createNdviJob_retriesOnServerErrorAndThenSucceeds() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(500)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"detail\":\"temporary\"}"));
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"jobId\":\"job-123\",\"status\":\"accepted\"}"));

        OpenEoJobSubmissionResult result = client.createNdviJob("region-1", "SIM-RA-01", "2026-01-01", "2026-01-31");

        assertEquals("job-123", result.getJobId());
        assertEquals("accepted", result.getStatus());
        assertEquals(2, server.getRequestCount());
        RecordedRequest request = server.takeRequest();
        assertEquals("/jobs/ndvi", request.getPath());
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"aoi\":\"SIM-RA-01\""));
    }

    @Test
    void createNdmiJob_doesNotRetryOnClientError() {
        server.enqueue(new MockResponse()
            .setResponseCode(400)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"detail\":\"bad request\"}"));

        assertThrows(
            OpenEoClientException.class,
            () -> client.createNdmiJob("region-1", null, "2026-01-01", "2026-01-31")
        );

        assertEquals(1, server.getRequestCount());
    }

    @Test
    void createNdviJob_parsesInlineValueWhenAvailable() {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"jobId\":\"job-999\",\"status\":\"completed\",\"value\":0.72,\"unit\":\"index\",\"quality\":\"high\"}"));

        OpenEoJobSubmissionResult result = client.createNdviJob("region-2", "AOI-2", "2026-02-01", "2026-02-28");

        assertEquals("job-999", result.getJobId());
        assertEquals("completed", result.getStatus());
        assertEquals(0.72, result.getValue());
        assertNotNull(result.getUnit());
    }

    @Test
    void fetchLatestIndicator_parsesRealPayload() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                "{\"value\":0.43,\"measuredAt\":\"2026-04-10T12:00:00Z\",\"collectionId\":\"sentinel-2\",\"cached\":true,\"source\":\"openEO\",\"quality\":\"measured\"}"
            ));

        OpenEoIndicatorLatestRequest request = new OpenEoIndicatorLatestRequest();
        request.setRegionId("region-15");
        request.setAoi(new OpenEoIndicatorLatestRequest.AoiRequest("bbox", java.util.List.of(-70.8, -19.2, -69.2, -18.1)));
        request.setPeriodStart("2026-04-01");
        request.setPeriodEnd("2026-04-10");

        OpenEoIndicatorLatestResponse response = client.fetchLatestIndicator(IndicatorType.NDMI, request);

        assertEquals(0.43, response.getValue());
        assertEquals("sentinel-2", response.getCollectionId());
        assertEquals("openEO", response.getSource());
        assertEquals("measured", response.getQuality());
        assertEquals(true, response.getCached());
        assertEquals(LocalDateTime.parse("2026-04-10T12:00:00"), response.getMeasuredAt());
        assertEquals("/openeo/indicators/latest/NDMI", server.takeRequest().getPath());
    }
}
