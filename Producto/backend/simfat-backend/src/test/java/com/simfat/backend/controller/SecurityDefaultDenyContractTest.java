package com.simfat.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.simfat.backend.dto.OpenEoMeasurementIngestResponseDTO;
import com.simfat.backend.repository.CitizenReportRepository;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.ForestLossRecordRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.repository.RegionRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import com.simfat.backend.security.PublicEndpointPaths;
import com.simfat.backend.service.ComunaRiskService;
import com.simfat.backend.service.NasaFirmsService;
import com.simfat.backend.service.OpenEoIngestService;
import com.simfat.backend.service.OpenWeatherFwiService;
import com.simfat.backend.service.TerritoryRiskService;
import jakarta.servlet.ServletException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Contract for the API authorization boundary: everything is denied to anonymous callers
 * (401) except the explicit allowlist. The negative half enumerates the real handler mappings,
 * so a new endpoint cannot be reachable anonymously by accident.
 */
@SpringBootTest(properties = "openeo.ingest.auth-token=" + SecurityDefaultDenyContractTest.INGEST_TOKEN)
@AutoConfigureMockMvc
class SecurityDefaultDenyContractTest {

    static final String INGEST_TOKEN = "contract-test-ingest-token";

    /** Expected allowlist, deliberately duplicated: changing PublicEndpointPaths must change this too. */
    private static final Set<String> EXPECTED_ALLOWLIST = new TreeSet<>(List.of(
        "OPTIONS /**",
        "ANY /v3/api-docs/**",
        "ANY /swagger-ui/**",
        "ANY /swagger-ui.html",
        "POST /api/auth/register",
        "POST /api/auth/login",
        "POST /api/auth/forgot-password",
        "POST /api/auth/reset-password",
        "POST /api/auth/refresh",
        "POST /api/auth/dev/seed-users",
        "POST /api/indicators/measurements",
        "GET /api/alerts/public",
        "GET /api/citizen-reports/public",
        "GET /api/territory/public",
        "GET /api/territory/public/**",
        "GET /api/territory/risk-score/**",
        "GET /api/territory/geojson/**",
        "GET /geojson/**",
        "GET /uploads/citizen-reports/**"
    ));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    // Public endpoints reach real controller code: keep Mongo out of the picture.
    @MockBean
    private HeatAlertEventRepository heatAlertEventRepository;
    @MockBean
    private CitizenReportRepository citizenReportRepository;
    @MockBean
    private ForestLossRecordRepository forestLossRecordRepository;
    @MockBean
    private OpenEoIndicatorObservationRepository openEoIndicatorObservationRepository;
    @MockBean
    private RegionRepository regionRepository;
    @MockBean
    private ComunaInfoRepository comunaInfoRepository;
    @MockBean
    private TerritoryWeatherObservationRepository territoryWeatherObservationRepository;
    @MockBean
    private TerritoryRiskService territoryRiskService;
    @MockBean
    private NasaFirmsService nasaFirmsService;
    @MockBean
    private OpenWeatherFwiService openWeatherFwiService;
    @MockBean
    private ComunaRiskService comunaRiskService;
    @MockBean
    private OpenEoIngestService openEoIngestService;

    @Test
    void allowlistMirrorsPublicEndpointPaths() {
        Set<String> actual = new TreeSet<>();
        PublicEndpointPaths.RULES.forEach(rule -> actual.add(rule.toString()));
        assertThat(actual).isEqualTo(EXPECTED_ALLOWLIST);
    }

    @Test
    void everyNonAllowlistedHandlerReturnsUnauthorizedToAnonymousCallers() throws Exception {
        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (String[] target : enumerateHandlerTargets()) {
            String method = target[0];
            String path = target[1];
            if (PublicEndpointPaths.isPublic(method, path)) {
                continue;
            }
            checked++;
            int status = statusOf(request(HttpMethod.valueOf(method), path));
            if (status != 401) {
                violations.add(method + " " + path + " -> " + status);
            }
        }
        assertThat(checked).as("handler mappings inspected").isGreaterThan(20);
        assertThat(violations).as("non-allowlisted endpoints reachable anonymously").isEmpty();
    }

    @Test
    void namedSensitiveEndpointsRequireAuthentication() throws Exception {
        for (String path : List.of(
            "/api/community/contacts",
            "/api/community/board",
            "/api/citizen-reports",
            "/api/alerts/map",
            "/api/dashboard/summary",
            "/api/regions"
        )) {
            assertThat(statusOf(request(HttpMethod.GET, path))).as("GET " + path).isEqualTo(401);
        }
    }

    @Test
    void allowlistedGetPathsAreNotRejectedBySecurity() throws Exception {
        for (String path : List.of(
            "/api/alerts/public?regionId=biobio",
            "/api/citizen-reports/public",
            "/api/territory/public/layers?regionId=biobio",
            "/api/territory/public/bounds",
            "/api/territory/risk-score/biobio",
            "/api/territory/risk-score/comunas/biobio",
            "/api/territory/geojson/biobio",
            "/geojson/does-not-exist.json",
            "/uploads/citizen-reports/does-not-exist.jpg",
            "/swagger-ui/index.html",
            "/swagger-ui.html",
            "/v3/api-docs"
        )) {
            assertThat(statusOf(request(HttpMethod.GET, path))).as("GET " + path).isNotIn(401, 403);
        }
    }

    @Test
    void corsPreflightOnProtectedPathIsNotRejected() throws Exception {
        int status = statusOf(options("/api/community/contacts")
            .header("Origin", "http://localhost:3000")
            .header("Access-Control-Request-Method", "GET"));
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void authPostEndpointsAreNotRejectedByUrlSecurity() throws Exception {
        for (String path : List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/refresh"
        )) {
            int status = statusOf(post(path).contentType("application/json").content("{}"));
            assertThat(status).as("POST " + path + " with invalid body").isNotIn(401, 403);
        }
    }

    @Test
    void ingestPostReachesTheControllerAndItsOwnTokenCheck() throws Exception {
        String body = """
            {"regionId":"biobio","indicatorType":"NDVI","periodStart":"2026-01-01","periodEnd":"2026-01-31"}
            """;
        // Wrong ingest token: rejected by the controller (UnauthorizedException), which only runs if URL security let it through.
        MvcResult wrong = mockMvc.perform(post("/api/indicators/measurements")
                .header("X-OpenEO-Ingest-Token", "wrong-token")
                .contentType("application/json")
                .content(body))
            .andReturn();
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrong.getResponse().getContentAsString()).contains("ingesta interna");

        // Correct ingest token without any JWT: only passes if the ingest rule is in the allowlist.
        when(openEoIngestService.ingestMeasurement(any())).thenReturn(new OpenEoMeasurementIngestResponseDTO());
        int status = statusOf(post("/api/indicators/measurements")
            .header("X-OpenEO-Ingest-Token", INGEST_TOKEN)
            .contentType("application/json")
            .content(body));
        assertThat(status).isEqualTo(201);
        verify(openEoIngestService).ingestMeasurement(any());
    }

    @Test
    void nonGetMethodsOnGetOnlyPublicPathsAreUnauthorized() throws Exception {
        assertThat(statusOf(request(HttpMethod.POST, "/api/territory/public"))).isEqualTo(401);
        assertThat(statusOf(request(HttpMethod.PUT, "/api/territory/public/bounds"))).isEqualTo(401);
        assertThat(statusOf(request(HttpMethod.DELETE, "/api/territory/public/layers"))).isEqualTo(401);
        assertThat(statusOf(request(HttpMethod.POST, "/uploads/citizen-reports/x.jpg"))).isEqualTo(401);
    }

    @Test
    void staleBearerTokenOnPublicPathIsServedAnonymously() throws Exception {
        for (String path : List.of("/api/territory/public/layers", "/api/territory/public/bounds")) {
            int status = statusOf(request(HttpMethod.GET, path).header("Authorization", "Bearer garbage.token.value"));
            assertThat(status).as("GET " + path + " with stale token").isNotIn(401, 403);
        }
    }

    @Test
    void publicSegmentOnOtherResourcesIsNotAllowlisted() throws Exception {
        // Public routes are opt-in per resource; a "public" segment elsewhere grants nothing.
        assertThat(PublicEndpointPaths.isPublic("GET", "/api/community/public/x")).isFalse();
    }

    @Test
    void publicAlertsAndReportsAllowlistIsExact() throws Exception {
        // Only the exact GET paths are public: no sub-paths, no write methods.
        assertThat(PublicEndpointPaths.isPublic("GET", "/api/alerts/public/x")).isFalse();
        assertThat(PublicEndpointPaths.isPublic("GET", "/api/citizen-reports/public/x")).isFalse();
        assertThat(PublicEndpointPaths.isPublic("POST", "/api/alerts/public")).isFalse();
        assertThat(PublicEndpointPaths.isPublic("POST", "/api/citizen-reports/public")).isFalse();
        assertThat(statusOf(request(HttpMethod.GET, "/api/alerts/public/x"))).isEqualTo(401);
        assertThat(statusOf(request(HttpMethod.GET, "/api/citizen-reports/public/x"))).isEqualTo(401);
        assertThat(statusOf(request(HttpMethod.POST, "/api/alerts/public"))).isEqualTo(401);
        assertThat(statusOf(request(HttpMethod.POST, "/api/citizen-reports/public"))).isEqualTo(401);
    }

    @Test
    void staleBearerTokenOnProtectedPathStillReturnsUnauthorized() throws Exception {
        int status = statusOf(request(HttpMethod.GET, "/api/community/contacts")
            .header("Authorization", "Bearer garbage.token.value"));
        assertThat(status).isEqualTo(401);
    }

    private List<String[]> enumerateHandlerTargets() {
        Set<String> seen = new LinkedHashSet<>();
        List<String[]> targets = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            Set<String> patterns = info.getPathPatternsCondition() != null
                ? info.getPathPatternsCondition().getPatternValues()
                : Set.of();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            List<String> methodNames = new ArrayList<>();
            if (methods.isEmpty()) {
                methodNames.add("GET");
            } else {
                methods.forEach(m -> methodNames.add(m.name()));
            }
            for (String pattern : patterns) {
                String concrete = pattern.replaceAll("\\{[^}]*\\}", "x").replace("*", "x");
                for (String method : methodNames) {
                    if ("OPTIONS".equals(method)) {
                        continue;
                    }
                    if (seen.add(method + " " + concrete)) {
                        targets.add(new String[] {method, concrete});
                    }
                }
            }
        }
        return targets;
    }

    /**
     * Performs the request and returns the HTTP status. An exception thrown by controller code
     * happens after the authorization filter let the request through, so it is reported as 500
     * and can never be mistaken for a 401/403 decision.
     */
    private int statusOf(MockHttpServletRequestBuilder builder) throws Exception {
        try {
            return mockMvc.perform(builder).andReturn().getResponse().getStatus();
        } catch (ServletException ex) {
            return 500;
        }
    }
}
