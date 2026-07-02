package com.simfat.backend.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mongodb.bulk.BulkWriteResult;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;

// Mockito-based unit tests for BackfillComunaIdRunner — complements
// BackfillComunaIdRunnerIntegrationTest (@DataMongoTest, real $geoIntersects) with
// scenarios that need to inject failures or inspect log output, which a real Mongo
// integration test cannot do cleanly.
@ExtendWith(MockitoExtension.class)
class BackfillComunaIdRunnerTest {

    @Mock
    private HeatAlertEventRepository heatAlertEventRepository;
    @Mock
    private ComunaInfoRepository comunaInfoRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private BulkOperations bulkOperations;

    private BackfillComunaIdRunner runner;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger runnerLogger;

    @BeforeEach
    void setUp() {
        runner = new BackfillComunaIdRunner(heatAlertEventRepository, comunaInfoRepository, mongoTemplate);
        runner.setBackfillEnabled(true);

        runnerLogger = (Logger) LoggerFactory.getLogger(BackfillComunaIdRunner.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        runnerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        runnerLogger.detachAppender(logAppender);
    }

    private HeatAlertEvent event(Double lat, Double lon) {
        HeatAlertEvent e = new HeatAlertEvent();
        e.setId("evt-" + System.nanoTime());
        e.setRegionId("region-1");
        e.setLatitud(lat);
        e.setLongitud(lon);
        e.setFechaEvento(LocalDateTime.now().minusHours(1));
        e.setFuente("NASA_FIRMS");
        return e;
    }

    // FIX 3 (finding C3): a mid-run exception must be caught and logged with
    // status=failed, including partial progress counters — not silently swallowed by the
    // default @Async exception handler (which only logs a generic Spring line, no
    // status=failed, no operator signal).
    @Test
    void backfill_exceptionMidRun_logsStatusFailedWithPartialProgress_doesNotPropagate() {
        when(heatAlertEventRepository.streamByFuenteAndComunaIdIsNull("NASA_FIRMS"))
            .thenReturn(Stream.of(event(-37.5, -72.5)));
        when(comunaInfoRepository.findOneByGeometryIntersects(any()))
            .thenThrow(new RuntimeException("simulated MongoBulkWriteException"));

        assertDoesNotThrow(() -> runner.backfill());

        List<ILoggingEvent> failedLogs = logAppender.list.stream()
            .filter(e -> e.getLevel() == Level.ERROR)
            .filter(e -> e.getFormattedMessage().contains("status=failed"))
            .toList();
        org.junit.jupiter.api.Assertions.assertEquals(1, failedLogs.size());
    }

    // FIX 8 (finding C10): rows with null lat/lon are skipped forever with no counter and
    // no logging. Add a skippedNoCoords counter included in the final status=done line.
    @Test
    void backfill_rowsWithNullCoords_skippedNoCoordsCounterLoggedInStatusDone() {
        when(heatAlertEventRepository.streamByFuenteAndComunaIdIsNull("NASA_FIRMS"))
            .thenReturn(Stream.of(event(null, null), event(null, -72.5), event(-37.5, null)));

        runner.backfill();

        List<ILoggingEvent> doneLogs = logAppender.list.stream()
            .filter(e -> e.getLevel() == Level.INFO)
            .filter(e -> e.getFormattedMessage().contains("status=done"))
            .toList();
        org.junit.jupiter.api.Assertions.assertEquals(1, doneLogs.size());
        String message = doneLogs.get(0).getFormattedMessage();
        org.junit.jupiter.api.Assertions.assertTrue(
            message.contains("skippedNoCoords=3"),
            "expected skippedNoCoords=3 in: " + message
        );
        verify(comunaInfoRepository, never()).findOneByGeometryIntersects(any());
    }

    // FIX 9 (finding C11): the trailing partial-batch bulk.execute() result was discarded
    // — unlike every in-loop batch, which logs matched/modified. Capture and log it the
    // same way before the final status=done summary.
    @Test
    void backfill_trailingPartialBatch_resultIsCapturedAndLoggedBeforeStatusDone() {
        when(heatAlertEventRepository.streamByFuenteAndComunaIdIsNull("NASA_FIRMS"))
            .thenReturn(Stream.of(event(-37.5, -72.5)));
        when(comunaInfoRepository.findOneByGeometryIntersects(any())).thenReturn(java.util.Optional.empty());
        when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED), eq(HeatAlertEvent.class)))
            .thenReturn(bulkOperations);
        when(bulkOperations.updateOne(any(), any())).thenReturn(bulkOperations);
        BulkWriteResult result = mock(BulkWriteResult.class);
        when(result.getMatchedCount()).thenReturn(1);
        when(result.getModifiedCount()).thenReturn(1);
        when(bulkOperations.execute()).thenReturn(result);

        runner.backfill();

        verify(bulkOperations, times(1)).execute();
        List<ILoggingEvent> progressLogs = logAppender.list.stream()
            .filter(e -> e.getLevel() == Level.INFO)
            .filter(e -> e.getFormattedMessage().contains("status=progress"))
            .toList();
        org.junit.jupiter.api.Assertions.assertEquals(1, progressLogs.size());
        org.junit.jupiter.api.Assertions.assertTrue(progressLogs.get(0).getFormattedMessage().contains("matched=1"));
        org.junit.jupiter.api.Assertions.assertTrue(progressLogs.get(0).getFormattedMessage().contains("modified=1"));
    }
}
