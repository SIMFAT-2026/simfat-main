package com.simfat.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** A missing static file is a 404 with the project's ApiError shape, not a 500. */
class MissingStaticResourceIntegrationTest extends MongoFreeWebTestSupport {

    @Test
    void missingUploadIsNotFoundWithApiErrorShape() throws Exception {
        mockMvc.perform(get("/uploads/citizen-reports/missing.jpg"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.path").value("/uploads/citizen-reports/missing.jpg"));
    }

    @Test
    void missingGeojsonIsNotFound() throws Exception {
        mockMvc.perform(get("/geojson/x.json"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void otherHandlersKeepTheirStatus() throws Exception {
        // 405 handler is untouched by the 404 mapping.
        mockMvc.perform(post("/api/territory/public/layers")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("u")))
            .andExpect(status().isMethodNotAllowed());
    }
}
