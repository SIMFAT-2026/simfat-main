package com.simfat.backend.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.simfat.backend.repository.AppRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PostgresKeepAliveJobTest {

    private final AppRoleRepository appRoleRepository = mock(AppRoleRepository.class);
    private final Logger logger = (Logger) LoggerFactory.getLogger(PostgresKeepAliveJob.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void run_whenEnabled_queriesRepositoryOnceAndLogsOk() {
        when(appRoleRepository.count()).thenReturn(5L);

        new PostgresKeepAliveJob(appRoleRepository, true).run();

        verify(appRoleRepository, times(1)).count();
        assertEquals(1, appender.list.size());
        assertEquals(Level.INFO, appender.list.get(0).getLevel());
        assertTrue(appender.list.get(0).getFormattedMessage().contains("status=ok"));
    }

    @Test
    void run_whenQueryFails_logsSingleWarnAndDoesNotRethrow() {
        when(appRoleRepository.count()).thenThrow(new IllegalStateException("tenant not found"));

        assertDoesNotThrow(() -> new PostgresKeepAliveJob(appRoleRepository, true).run());

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("status=failed"));
        assertTrue(event.getFormattedMessage().contains("IllegalStateException"));
    }

    @Test
    void run_whenDisabled_doesNotQuery() {
        new PostgresKeepAliveJob(appRoleRepository, false).run();

        verify(appRoleRepository, never()).count();
        assertTrue(appender.list.isEmpty());
    }
}
