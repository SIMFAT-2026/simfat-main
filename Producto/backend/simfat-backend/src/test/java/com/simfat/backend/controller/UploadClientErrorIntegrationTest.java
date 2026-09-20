package com.simfat.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.simfat.backend.exception.ApiError;
import com.simfat.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Upload client errors are 4xx with the ApiError shape, logged at DEBUG, never a logged 500. */
@WithMockUser(authorities = "PERM_REPORT_CREATE")
class UploadClientErrorIntegrationTest extends MongoFreeWebTestSupport {

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
    void missingPayloadPartIsBadRequestNamingThePart() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/citizen-reports").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value(containsString("payload")))
            .andExpect(jsonPath("$.path").value("/api/citizen-reports"));

        assertThat(appender.list).noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));
    }

    @Test
    void unsupportedContentTypeIsUnsupportedMediaTypeWithoutEchoingTheValue() throws Exception {
        mockMvc.perform(post("/api/citizen-reports").contentType(MediaType.TEXT_PLAIN).content("x"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value(415))
            .andExpect(jsonPath("$.path").value("/api/citizen-reports"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(containsString("text/plain"))));

        assertThat(appender.list).noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));
    }

    // A real oversized multipart request cannot be produced through MockMvc (the size limit is enforced by
    // the servlet container / multipart resolver), so the handler is exercised directly.
    @Test
    void oversizedUploadHandlerReturnsPayloadTooLargeAtDebugLevel() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/citizen-reports");

        ResponseEntity<ApiError> response = new GlobalExceptionHandler()
            .handleMaxUploadSize(new MaxUploadSizeExceededException(1024L), request);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody().getStatus()).isEqualTo(413);
        assertThat(response.getBody().getPath()).isEqualTo("/api/citizen-reports");
        assertThat(response.getBody().getMessage()).doesNotContain("1024");
        assertThat(appender.list).noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));
    }
}
