package com.simfat.backend.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.service.fwi.ComunaFwiAdvanceResult;
import com.simfat.backend.service.fwi.ComunaFwiStateService;
import com.simfat.backend.service.fwi.FwiInputs;
import com.simfat.backend.service.fwi.FwiOutputs;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FwiSpinUpRunnerTest {

    private static final ZoneId SANTIAGO_ZONE = ZoneId.of("America/Santiago");

    // Must mirror FwiSpinUpRunner.ARCHIVE_LATENCY_DAYS so tests compute the same window.
    private static final int ARCHIVE_LATENCY_DAYS = 2;

    private MockWebServer server;

    @Mock
    private ComunaInfoRepository comunaInfoRepository;
    @Mock
    private ComunaFwiStateService comunaFwiStateService;

    private FwiSpinUpRunner runner;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger runnerLogger;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        runner = new FwiSpinUpRunner(comunaInfoRepository, comunaFwiStateService, new ObjectMapper());
        ReflectionTestUtils.setField(runner, "archiveBaseUrl", server.url("/").toString().replaceAll("/$", ""));
        ReflectionTestUtils.setField(runner, "lookbackDays", 3);
        runner.setSpinUpEnabled(true);

        // Default stub: a plain, non-flagged successful advance for any comuna/day not given a
        // more specific stub in an individual test. Real production advance() never returns
        // null (S1d1 contract); tests that ignored the return value before this fix now need
        // SOME non-null result once the runner starts reading qualityFlag() off it.
        lenient().when(comunaFwiStateService.advance(any(), any(), any())).thenReturn(advanceResult(null));

        runnerLogger = (Logger) LoggerFactory.getLogger(FwiSpinUpRunner.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        runnerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
        runnerLogger.detachAppender(logAppender);
    }

    private List<ILoggingEvent> logsContaining(String fragment) {
        return logAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains(fragment))
                .toList();
    }

    private ComunaFwiAdvanceResult advanceResult(String qualityFlag) {
        return new ComunaFwiAdvanceResult(new FwiOutputs(85.0, 6.0, 15.0, 0.0, 0.0, 0.0, 0.0), qualityFlag);
    }

    private ComunaInfo comuna(String id, double lat, double lon) {
        ComunaInfo c = new ComunaInfo();
        c.setId(id);
        c.setCenterLat(lat);
        c.setCenterLon(lon);
        return c;
    }

    private LocalDate endDate() {
        return LocalDate.now(SANTIAGO_ZONE).minusDays(ARCHIVE_LATENCY_DAYS);
    }

    private String archiveBody(
            LocalDate startDate, LocalDate endDate, double[] temps, double[] rh, double[] wind, double[] precip) {
        StringBuilder times = new StringBuilder();
        StringBuilder tempJson = new StringBuilder();
        StringBuilder rhJson = new StringBuilder();
        StringBuilder windJson = new StringBuilder();
        StringBuilder dailyTimes = new StringBuilder();
        StringBuilder precipJson = new StringBuilder();
        int i = 0;
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1), i++) {
            if (i > 0) {
                times.append(",");
                tempJson.append(",");
                rhJson.append(",");
                windJson.append(",");
                dailyTimes.append(",");
                precipJson.append(",");
            }
            times.append("\"").append(d).append("T12:00\"");
            tempJson.append(temps[i]);
            rhJson.append(rh[i]);
            windJson.append(wind[i]);
            dailyTimes.append("\"").append(d).append("\"");
            precipJson.append(precip[i]);
        }
        return "{"
                + "\"daily\":{\"time\":[" + dailyTimes + "],\"precipitation_sum\":[" + precipJson + "]},"
                + "\"hourly\":{\"time\":[" + times + "],"
                + "\"temperature_2m\":[" + tempJson + "],"
                + "\"relative_humidity_2m\":[" + rhJson + "],"
                + "\"wind_speed_10m\":[" + windJson + "]}"
                + "}";
    }

    @Test
    void run_disabledByDefault_doesNothing() {
        runner.setSpinUpEnabled(false);

        runner.run(new DefaultApplicationArguments());

        verifyNoInteractions(comunaInfoRepository, comunaFwiStateService);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void run_enabled_advancesEachDayInAscendingOrderWithDerivedInputs() throws InterruptedException {
        LocalDate end = endDate();
        LocalDate start = end.minusDays(2);

        when(comunaInfoRepository.findAll()).thenReturn(List.of(comuna("comuna-1", -38.0, -72.0)));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(archiveBody(
                        start, end,
                        new double[] {10.0, 15.0, 20.0},
                        new double[] {80.0, 60.0, 40.0},
                        new double[] {5.0, 10.0, 15.0},
                        new double[] {1.0, 0.0, 2.0})));

        runner.run(new DefaultApplicationArguments());

        InOrder order = inOrder(comunaFwiStateService);
        order.verify(comunaFwiStateService).advance(eq("comuna-1"), eq(start),
                eq(new FwiInputs(10.0, 80.0, 5.0, 1.0, start.getMonthValue())));
        order.verify(comunaFwiStateService).advance(eq("comuna-1"), eq(start.plusDays(1)),
                eq(new FwiInputs(15.0, 60.0, 10.0, 0.0, start.plusDays(1).getMonthValue())));
        order.verify(comunaFwiStateService).advance(eq("comuna-1"), eq(end),
                eq(new FwiInputs(20.0, 40.0, 15.0, 2.0, end.getMonthValue())));

        assertEquals(1, server.getRequestCount());
        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("/v1/archive"));
        assertTrue(request.getPath().contains("start_date=" + start));
        assertTrue(request.getPath().contains("end_date=" + end));
    }

    @Test
    void run_oneComunaHttpError_isolatesFailureAndStillProcessesOtherComunas() throws InterruptedException {
        LocalDate end = endDate();
        LocalDate start = end.minusDays(2);

        when(comunaInfoRepository.findAll()).thenReturn(List.of(
                comuna("comuna-bad", -38.0, -72.0),
                comuna("comuna-good", -37.0, -71.0)
        ));

        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(archiveBody(
                        start, end,
                        new double[] {12.0, 13.0, 14.0},
                        new double[] {70.0, 65.0, 55.0},
                        new double[] {8.0, 9.0, 11.0},
                        new double[] {0.0, 0.0, 0.0})));

        assertDoesNotThrow(() -> runner.run(new DefaultApplicationArguments()));

        verify(comunaFwiStateService, never()).advance(eq("comuna-bad"), any(), any());
        verify(comunaFwiStateService).advance(eq("comuna-good"), eq(start), any());
        verify(comunaFwiStateService).advance(eq("comuna-good"), eq(start.plusDays(1)), any());
        verify(comunaFwiStateService).advance(eq("comuna-good"), eq(end), any());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void run_advanceThrowsForAlreadySpunUpComuna_doesNotPropagateAndOtherComunasStillProcessed()
            throws InterruptedException {
        // Idempotency semantics (S1d1): re-running the spin-up against a comuna that already
        // has newer state throws IllegalArgumentException from ComunaFwiStateService.advance
        // ("moving the FWI chain backwards is not supported") on the very first day the
        // runner attempts. The runner must catch it per-comuna, persist nothing further for
        // that comuna in this run, and keep processing the rest -- never corrupt state and
        // never abort the whole spin-up over one already-warm comuna.
        LocalDate end = endDate();
        LocalDate start = end.minusDays(2);

        when(comunaInfoRepository.findAll()).thenReturn(List.of(
                comuna("comuna-already-spun-up", -38.0, -72.0),
                comuna("comuna-fresh", -37.0, -71.0)
        ));

        when(comunaFwiStateService.advance(eq("comuna-already-spun-up"), any(), any()))
                .thenThrow(new IllegalArgumentException(
                        "targetDate is before the stored stateDate -- moving the FWI chain backwards"
                                + " is not supported"));

        String body = archiveBody(
                start, end,
                new double[] {12.0, 13.0, 14.0},
                new double[] {70.0, 65.0, 55.0},
                new double[] {8.0, 9.0, 11.0},
                new double[] {0.0, 0.0, 0.0});
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json").setBody(body));
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json").setBody(body));

        assertDoesNotThrow(() -> runner.run(new DefaultApplicationArguments()));

        verify(comunaFwiStateService, times(1))
                .advance(eq("comuna-already-spun-up"), eq(start), any());
        verify(comunaFwiStateService, never())
                .advance(eq("comuna-already-spun-up"), eq(start.plusDays(1)), any());
        verify(comunaFwiStateService).advance(eq("comuna-fresh"), eq(start), any());
        verify(comunaFwiStateService).advance(eq("comuna-fresh"), eq(start.plusDays(1)), any());
        verify(comunaFwiStateService).advance(eq("comuna-fresh"), eq(end), any());
    }

    @Test
    void run_missingNoonDataForOneDay_skipsThatDayButAdvancesOtherDays() {
        LocalDate end = endDate();
        LocalDate start = end.minusDays(1);
        ReflectionTestUtils.setField(runner, "lookbackDays", 2);

        when(comunaInfoRepository.findAll()).thenReturn(List.of(comuna("comuna-1", -38.0, -72.0)));

        // Only the FIRST day has hourly noon data; the second day's noon timestamp is simply
        // absent from the hourly.time array (a real Open-Meteo gap), so the noon-index lookup
        // returns null for `end` and that day is skipped rather than fabricated.
        String body = "{"
                + "\"daily\":{\"time\":[\"" + start + "\",\"" + end + "\"],\"precipitation_sum\":[0.0,0.0]},"
                + "\"hourly\":{\"time\":[\"" + start + "T12:00\"],"
                + "\"temperature_2m\":[16.0],"
                + "\"relative_humidity_2m\":[50.0],"
                + "\"wind_speed_10m\":[7.0]}"
                + "}";
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json").setBody(body));

        runner.run(new DefaultApplicationArguments());

        verify(comunaFwiStateService).advance(eq("comuna-1"), eq(start), any());
        verify(comunaFwiStateService, never()).advance(eq("comuna-1"), eq(end), any());
    }

    private String archiveBodyOmittingDays(LocalDate start, LocalDate end, java.util.Set<LocalDate> omitDays) {
        StringBuilder times = new StringBuilder();
        StringBuilder tempJson = new StringBuilder();
        StringBuilder rhJson = new StringBuilder();
        StringBuilder windJson = new StringBuilder();
        StringBuilder dailyTimes = new StringBuilder();
        StringBuilder precipJson = new StringBuilder();
        boolean firstHourly = true;
        boolean firstDaily = true;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!firstDaily) {
                dailyTimes.append(",");
                precipJson.append(",");
            }
            dailyTimes.append("\"").append(d).append("\"");
            precipJson.append(0.0);
            firstDaily = false;

            if (omitDays.contains(d)) {
                continue;
            }
            if (!firstHourly) {
                times.append(",");
                tempJson.append(",");
                rhJson.append(",");
                windJson.append(",");
            }
            times.append("\"").append(d).append("T12:00\"");
            tempJson.append(15.0);
            rhJson.append(50.0);
            windJson.append(10.0);
            firstHourly = false;
        }
        return "{"
                + "\"daily\":{\"time\":[" + dailyTimes + "],\"precipitation_sum\":[" + precipJson + "]},"
                + "\"hourly\":{\"time\":[" + times + "],"
                + "\"temperature_2m\":[" + tempJson + "],"
                + "\"relative_humidity_2m\":[" + rhJson + "],"
                + "\"wind_speed_10m\":[" + windJson + "]}"
                + "}";
    }

    // CRITICAL fix: a single missing day in the MIDDLE of the window (not the last day) leaves
    // a gap of exactly 2 calendar days between the last successfully-advanced day and the next
    // attempted day. S1d1's ComunaFwiStateService#advance throws UnsupportedOperationException
    // for a gap in (1, maxGapDays] it cannot bridge from a single day's FwiInputs. Before this
    // fix that exception propagated uncaught out of spinUpComuna's day loop, was caught only by
    // run()'s generic per-comuna handler, and the comuna was lumped into comunasFailed with the
    // same status=comuna_error as a real failure -- masking that days BEFORE the hole were
    // actually persisted. The runner must stop attempting further days for that comuna (the
    // chain is stuck), log a distinct status=window_truncated_by_gap line naming how many days
    // were advanced before the hole, and count the comuna in a new comunasPartial bucket.
    @Test
    void run_gapOfTwoDaysMidWindow_truncatesWindowAndLogsDistinctStatus() {
        LocalDate end = endDate();
        LocalDate start = end.minusDays(5);
        ReflectionTestUtils.setField(runner, "lookbackDays", 6);
        LocalDate missingDay = start.plusDays(1);
        LocalDate nextAttemptedDay = start.plusDays(2);

        when(comunaInfoRepository.findAll()).thenReturn(List.of(comuna("comuna-1", -38.0, -72.0)));
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(archiveBodyOmittingDays(start, end, java.util.Set.of(missingDay))));

        when(comunaFwiStateService.advance(eq("comuna-1"), eq(start), any()))
                .thenReturn(advanceResult(null));
        when(comunaFwiStateService.advance(eq("comuna-1"), eq(nextAttemptedDay), any()))
                .thenThrow(new UnsupportedOperationException(
                        "Gap of 2 day(s) for comuna comuna-1 is not supported by S1d1"));

        runner.run(new DefaultApplicationArguments());

        verify(comunaFwiStateService, times(1)).advance(eq("comuna-1"), eq(start), any());
        verify(comunaFwiStateService, times(1)).advance(eq("comuna-1"), eq(nextAttemptedDay), any());
        verify(comunaFwiStateService, never())
                .advance(eq("comuna-1"), eq(nextAttemptedDay.plusDays(1)), any());
        verify(comunaFwiStateService, never()).advance(eq("comuna-1"), eq(end), any());

        List<ILoggingEvent> truncatedLogs = logsContaining("status=window_truncated_by_gap");
        assertEquals(1, truncatedLogs.size());
        String message = truncatedLogs.get(0).getFormattedMessage();
        assertTrue(message.contains("comunaId=comuna-1"), message);
        assertTrue(message.contains("daysAdvanced=1"), message);
        assertTrue(message.contains("skippedDate=" + missingDay), message);

        List<ILoggingEvent> doneLogs = logsContaining("status=done");
        assertEquals(1, doneLogs.size());
        assertTrue(doneLogs.get(0).getFormattedMessage().contains("comunasPartial=1"),
                doneLogs.get(0).getFormattedMessage());
    }

    // CRITICAL fix: 3+ CONSECUTIVE missing days mid-window leave a gap beyond maxGapDays
    // (default 3), which S1d1's advance() bridges SILENTLY via coldStart -- no exception, the
    // ComunaFwiAdvanceResult carries qualityFlag="FWI_RESTARTED" but the chain restarts from
    // published startup values, discarding all real warm-up progress made earlier in this same
    // run. Before this fix the runner never inspected qualityFlag, so this looked exactly like
    // a normal comunasOk outcome. The runner must detect a restart flag that occurs AFTER at
    // least one day was already advanced this run, log a distinct status=window_restarted_by_gap
    // line, and count the comuna in the same new comunasPartial bucket.
    @Test
    void run_gapOfFourDaysMidWindow_detectsSilentRestartAndLogsDistinctStatus() {
        LocalDate end = endDate();
        LocalDate start = end.minusDays(5);
        ReflectionTestUtils.setField(runner, "lookbackDays", 6);
        LocalDate restartDay = start.plusDays(4);
        java.util.Set<LocalDate> missingDays = java.util.Set.of(
                start.plusDays(1), start.plusDays(2), start.plusDays(3));

        when(comunaInfoRepository.findAll()).thenReturn(List.of(comuna("comuna-1", -38.0, -72.0)));
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(archiveBodyOmittingDays(start, end, missingDays)));

        when(comunaFwiStateService.advance(eq("comuna-1"), eq(start), any()))
                .thenReturn(advanceResult(null));
        when(comunaFwiStateService.advance(eq("comuna-1"), eq(restartDay), any()))
                .thenReturn(advanceResult("FWI_RESTARTED"));
        when(comunaFwiStateService.advance(eq("comuna-1"), eq(end), any()))
                .thenReturn(advanceResult(null));

        runner.run(new DefaultApplicationArguments());

        verify(comunaFwiStateService, times(1)).advance(eq("comuna-1"), eq(start), any());
        verify(comunaFwiStateService, times(1)).advance(eq("comuna-1"), eq(restartDay), any());
        verify(comunaFwiStateService, times(1)).advance(eq("comuna-1"), eq(end), any());
        for (LocalDate missing : missingDays) {
            verify(comunaFwiStateService, never()).advance(eq("comuna-1"), eq(missing), any());
        }

        List<ILoggingEvent> restartedLogs = logsContaining("status=window_restarted_by_gap");
        assertEquals(1, restartedLogs.size());
        assertTrue(restartedLogs.get(0).getFormattedMessage().contains("comunaId=comuna-1"),
                restartedLogs.get(0).getFormattedMessage());

        List<ILoggingEvent> doneLogs = logsContaining("status=done");
        assertEquals(1, doneLogs.size());
        assertTrue(doneLogs.get(0).getFormattedMessage().contains("comunasPartial=1"),
                doneLogs.get(0).getFormattedMessage());
    }

    // WARNING fix: re-running the spin-up against an already-warm comuna is a SAFE, EXPECTED
    // outcome (S1d1's backwards-date rejection), not a real error. It must be logged distinctly
    // from status=comuna_error so an operator does not mistake it for a failure.
    @Test
    void run_advanceThrowsForAlreadySpunUpComuna_logsDistinctAlreadyUpToDateStatus() {
        LocalDate end = endDate();
        LocalDate start = end.minusDays(2);

        when(comunaInfoRepository.findAll()).thenReturn(List.of(comuna("comuna-already-spun-up", -38.0, -72.0)));
        when(comunaFwiStateService.advance(eq("comuna-already-spun-up"), any(), any()))
                .thenThrow(new IllegalArgumentException(
                        "targetDate is before the stored stateDate -- moving the FWI chain backwards"
                                + " is not supported"));

        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(archiveBody(
                        start, end,
                        new double[] {12.0, 13.0, 14.0},
                        new double[] {70.0, 65.0, 55.0},
                        new double[] {8.0, 9.0, 11.0},
                        new double[] {0.0, 0.0, 0.0})));

        runner.run(new DefaultApplicationArguments());

        List<ILoggingEvent> alreadyWarmLogs = logsContaining("status=already_up_to_date");
        assertEquals(1, alreadyWarmLogs.size());
        assertTrue(alreadyWarmLogs.get(0).getFormattedMessage().contains("comunaId=comuna-already-spun-up"),
                alreadyWarmLogs.get(0).getFormattedMessage());
        assertEquals(0, logsContaining("status=comuna_error").size());
    }

    // WARNING fix: the final summary must be loud (WARN) whenever anything in the run was not a
    // full clean success, and stay at INFO for a fully clean run.
    @Test
    void run_oneComunaFails_finalSummaryLoggedAtWarnLevel() {
        LocalDate end = endDate();
        LocalDate start = end.minusDays(2);

        when(comunaInfoRepository.findAll()).thenReturn(List.of(comuna("comuna-bad", -38.0, -72.0)));
        server.enqueue(new MockResponse().setResponseCode(500));

        runner.run(new DefaultApplicationArguments());

        List<ILoggingEvent> doneLogs = logAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("status=done"))
                .toList();
        assertEquals(1, doneLogs.size());
        assertEquals(Level.WARN, doneLogs.get(0).getLevel());
    }

    @Test
    void run_allComunasSucceed_finalSummaryLoggedAtInfoLevel() throws InterruptedException {
        LocalDate end = endDate();
        LocalDate start = end.minusDays(2);

        when(comunaInfoRepository.findAll()).thenReturn(List.of(comuna("comuna-1", -38.0, -72.0)));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(archiveBody(
                        start, end,
                        new double[] {10.0, 15.0, 20.0},
                        new double[] {80.0, 60.0, 40.0},
                        new double[] {5.0, 10.0, 15.0},
                        new double[] {1.0, 0.0, 2.0})));

        runner.run(new DefaultApplicationArguments());

        List<ILoggingEvent> doneLogs = logAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("status=done"))
                .toList();
        assertEquals(1, doneLogs.size());
        assertEquals(Level.INFO, doneLogs.get(0).getLevel());
    }
}
