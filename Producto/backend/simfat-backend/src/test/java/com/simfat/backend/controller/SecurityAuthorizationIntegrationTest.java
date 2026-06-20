package com.simfat.backend.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.simfat.backend.dto.AlertRuleResponseDTO;
import com.simfat.backend.service.AlertRuleService;
import com.simfat.backend.service.DashboardIndicatorService;
import com.simfat.backend.service.DashboardService;
import com.simfat.backend.service.OpenEoSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertRuleService alertRuleService;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private DashboardIndicatorService dashboardIndicatorService;

    @MockBean
    private OpenEoSyncService openEoSyncService;

    @Test
    void createRule_returnsForbidden_whenNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/rules")
                .contentType("application/json")
                .content(validRulePayload()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_COMMUNITY_USER"})
    void createRule_returnsForbidden_whenMissingPermission() throws Exception {
        mockMvc.perform(post("/api/rules")
                .contentType("application/json")
                .content(validRulePayload()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @WithMockUser(authorities = {"PERM_ALERT_RULE_MANAGE"})
    void createRule_returnsOk_whenPermissionPresent() throws Exception {
        AlertRuleResponseDTO response = new AlertRuleResponseDTO();
        response.setId("rule-1");
        response.setNombre("Regla test");
        response.setRegionId("region-1");
        response.setUmbralFwi(25.0);
        response.setActiva(true);
        when(alertRuleService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/rules")
                .contentType("application/json")
                .content(validRulePayload()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.id", is("rule-1")));
    }

    @Test
    void createRule_returnsUnauthorized_whenBearerTokenInvalid() throws Exception {
        mockMvc.perform(post("/api/rules")
                .header("Authorization", "Bearer invalid-token")
                .contentType("application/json")
                .content(validRulePayload()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status", is(401)));
    }

    private String validRulePayload() {
        return """
            {
              "nombre": "Regla test",
              "regionId": "region-1",
              "umbralFwi": 25.0,
              "activa": true
            }
            """;
    }
}
