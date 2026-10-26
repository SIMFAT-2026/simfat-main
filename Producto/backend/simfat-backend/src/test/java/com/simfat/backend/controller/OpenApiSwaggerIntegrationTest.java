package com.simfat.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSwaggerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsEndpoint_isPublic_andContainsExpectedMetadata() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openapi").exists())
            .andExpect(jsonPath("$.info.title").value("SIMFAT Backend API"))
            .andExpect(jsonPath("$.info.version").value("v1"));
    }

    @Test
    void apiDocsEndpoint_declaresDataLicenseAndCommercialContact() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.license.name").value("CC BY-NC 4.0 (datos de la API) · código MIT"))
            .andExpect(jsonPath("$.info.license.url").value("https://creativecommons.org/licenses/by-nc/4.0/"))
            .andExpect(jsonPath("$.info.contact.name").value("David Vásquez"))
            .andExpect(jsonPath("$.info.contact.email").value("d.vasquez.ovalle@gmail.com"))
            .andExpect(jsonPath("$.info.description").value(containsString("MapBiomas")));
    }

    @Test
    void swaggerUiIndex_isPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Swagger UI")));
    }
}
