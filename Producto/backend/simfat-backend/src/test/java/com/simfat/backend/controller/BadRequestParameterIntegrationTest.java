package com.simfat.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.simfat.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Missing/malformed request parameters are client errors: 400 with the ApiError shape, never a logged 500. */
class BadRequestParameterIntegrationTest extends MongoFreeWebTestSupport {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger handlerLogger;

    @BeforeEach
    void attachAppender() {
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender.start();
        handlerLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        handlerLogger.detachAppender(appender);
    }

    @Test
    void missingRegionIdIsBadRequestNamingTheParameter() throws Exception {
        mockMvc.perform(get("/api/alerts/public"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value(containsString("regionId")))
            .andExpect(jsonPath("$.path").value("/api/alerts/public"));
    }

    @Test
    void malformedDateIsBadRequestWithoutEchoingTheRawValue() throws Exception {
        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio").param("from", "<b>abc</b>"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value(containsString("from")))
            .andExpect(jsonPath("$.message").value(not(containsString("abc"))));
    }

    @Test
    void badParametersAreNotLoggedAsErrors() throws Exception {
        mockMvc.perform(get("/api/alerts/public")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio").param("from", "abc"))
            .andExpect(status().isBadRequest());

        assertThat(appender.list).noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));
    }
}
