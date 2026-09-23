package com.simfat.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.service.fwi.ComunaFwiStateService;
import com.simfat.backend.service.fwi.FwiInputs;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * One-shot, manually-triggered spin-up for {@code comuna_fwi_state} (S1e, design D4/D8,
 * task 1e.1). Backfills the Van Wagner FWI chain for every monitored comuna from Open-Meteo
 * Archive history BEFORE {@code territory.fwi.method} is ever flipped to {@code VAN_WAGNER},
 * so the chain is already past its cold-start transient ({@code FWI_WARMUP}) on cut-over day
 * instead of starting from the published startup values (FFMC=85/DMC=6/DC=15) with zero
 * history.
 *
 * <p><b>Trigger mechanism (deliberate choice):</b> a Spring Boot {@link ApplicationRunner},
 * NOT {@code @Scheduled} -- this is a manual, occasional operation, not a recurring job. It
 * runs once per application boot and is a no-op unless {@code territory.fwi.spinup.enabled}
 * is explicitly set to {@code true}, which an operator does for exactly one deploy cycle and
 * then reverts to {@code false} (see the property's own comment in
 * {@code application.properties}). An admin-triggered HTTP endpoint (the alternative
 * mechanism) was considered and rejected for this slice: it would add a new authenticated
 * route, a new controller method and a bigger security surface for an operation that happens
 * once (at most a handful of times across the whole project) -- a config flag plus a redeploy
 * gives the same one-shot control with none of that extra surface.
 *
 * <p><b>Idempotency:</b> each day is applied via {@link ComunaFwiStateService#advance}, which
 * is itself idempotent by calendar day (S1d1) and rejects moving the chain backwards
 * ({@link IllegalArgumentException}) or replaying a small gap it cannot fabricate
 * ({@link UnsupportedOperationException}). Re-running this spin-up against a comuna that
 * already has state from an earlier run -- same or overlapping window -- safely fails fast
 * for that comuna on the very first day (caught in {@link #run}, see the per-comuna isolation
 * below) instead of corrupting or duplicating its state; it never silently re-derives a
 * different history for an already-spun-up comuna. Re-running AFTER real time has advanced
 * the window forward resumes normally as ordinary daily advances.
 *
 * <p><b>Window (documented MVP choice):</b> not a full fire-season-start rule (S1d1's {@code
 * ComunaFwiStateService} does not implement one either, see its class javadoc) -- a fixed,
 * configurable {@code territory.fwi.spinup.lookback-days} (default 120) ending {@link
 * #ARCHIVE_LATENCY_DAYS} days before today, to stay clear of the Archive API's finalization
 * lag for the most recent days.
 *
 * <p><b>Per-comuna failure isolation:</b> one comuna's HTTP failure, missing data or {@code
 * advance} exception is logged and does NOT abort the whole spin-up run for the other 85 --
 * mirrors the per-region try/catch pattern already used by {@code
 * OpenWeatherFwiServiceImpl#syncFwiForAllRegions}.
 *
 * <p><b>Scope:</b> ONLY the spin-up backfill (task 1e.1). FWI threshold recalibration
 * (quantile matching, task 1e.2) and the CEMS cross-check (task 1e.3) are separate slices --
 * per decision Q24, Open-Meteo Archive is the sole historical source and the CEMS
 * cross-check is demoted to optional/deferred, not built here.
 */
@Component
public class FwiSpinUpRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(FwiSpinUpRunner.class);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ZoneId SANTIAGO_ZONE = ZoneId.of("America/Santiago");

    /**
     * Open-Meteo Archive reanalysis data for the most recent days is not yet finalized; this
     * buffer keeps the spin-up window clear of that lag (documented MVP choice, not a value
     * verified against Open-Meteo's own SLA).
     */
    private static final int ARCHIVE_LATENCY_DAYS = 2;

    private final ComunaInfoRepository comunaInfoRepository;
    private final ComunaFwiStateService comunaFwiStateService;
    private final ObjectMapper objectMapper;

    @Value("${openmeteo.archive.api.base-url:https://archive-api.open-meteo.com}")
    private String archiveBaseUrl;

    @Value("${territory.fwi.spinup.enabled:false}")
    private boolean spinUpEnabled;

    @Value("${territory.fwi.spinup.lookback-days:120}")
    private int lookbackDays;

    public FwiSpinUpRunner(
            ComunaInfoRepository comunaInfoRepository,
            ComunaFwiStateService comunaFwiStateService,
            ObjectMapper objectMapper
    ) {
        this.comunaInfoRepository = comunaInfoRepository;
        this.comunaFwiStateService = comunaFwiStateService;
        this.objectMapper = objectMapper;
    }

    // Test seam: @Value fields are normally only set by Spring; tests construct this
    // component directly and need to toggle the flag without a full application context
    // (same pattern as BackfillComunaIdRunner.setBackfillEnabled).
    void setSpinUpEnabled(boolean spinUpEnabled) {
        this.spinUpEnabled = spinUpEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!spinUpEnabled) {
            LOGGER.info("fwi_spinup status=skipped reason=disabled");
            return;
        }

        LocalDate endDate = LocalDate.now(SANTIAGO_ZONE).minusDays(ARCHIVE_LATENCY_DAYS);
        LocalDate startDate = endDate.minusDays(lookbackDays - 1L);

        List<ComunaInfo> comunas = comunaInfoRepository.findAll();
        LOGGER.info(
                "fwi_spinup status=starting comunaCount={} startDate={} endDate={}",
                comunas.size(), startDate, endDate
        );

        int comunasOk = 0;
        int comunasFailed = 0;
        for (ComunaInfo comuna : comunas) {
            try {
                spinUpComuna(comuna, startDate, endDate);
                comunasOk++;
            } catch (Exception ex) {
                comunasFailed++;
                LOGGER.warn(
                        "fwi_spinup status=comuna_error comunaId={} error={}",
                        comuna.getId(), ex.getMessage()
                );
            }
        }

        LOGGER.info(
                "fwi_spinup status=done comunasOk={} comunasFailed={} startDate={} endDate={}",
                comunasOk, comunasFailed, startDate, endDate
        );
    }

    /**
     * One Archive API call per comuna covering the whole window (86 requests total, far
     * inside the 600/min free-tier limit -- design D4), then one {@code advance} call per day
     * in ascending order so only the FINAL state is persisted per comuna, not one document per
     * observed day.
     */
    private void spinUpComuna(ComunaInfo comuna, LocalDate startDate, LocalDate endDate)
            throws IOException, InterruptedException {
        String url = archiveBaseUrl + "/v1/archive"
                + "?latitude=" + comuna.getCenterLat()
                + "&longitude=" + comuna.getCenterLon()
                + "&start_date=" + startDate
                + "&end_date=" + endDate
                + "&daily=precipitation_sum"
                + "&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m"
                + "&timezone=America%2FSantiago";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(
                    "archive_api_http_error status=" + response.statusCode() + " comunaId=" + comuna.getId());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode daily = root.path("daily");
        JsonNode hourly = root.path("hourly");

        int daysAdvanced = 0;
        int daysSkipped = 0;
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            FwiInputs inputs = deriveNoonInputsForDay(hourly, daily, day);
            if (inputs == null) {
                daysSkipped++;
                LOGGER.warn("fwi_spinup status=missing_day_inputs comunaId={} date={}", comuna.getId(), day);
                continue;
            }
            comunaFwiStateService.advance(comuna.getId(), day, inputs);
            daysAdvanced++;
        }

        LOGGER.info(
                "fwi_spinup status=comuna_done comunaId={} daysAdvanced={} daysSkipped={}",
                comuna.getId(), daysAdvanced, daysSkipped
        );
    }

    /**
     * Adapts S1d2's noon-extraction pattern ({@code OpenWeatherFwiServiceImpl
     * #deriveNoonInputs}: exact string match on {@code date+"T12:00"} against the hourly time
     * array) from a single "today" lookup to an arbitrary historical {@code day} within the
     * spin-up window.
     */
    private FwiInputs deriveNoonInputsForDay(JsonNode hourly, JsonNode daily, LocalDate day) {
        Integer noonIndex = findHourlyIndexForTimestamp(hourly, day + "T12:00");
        if (noonIndex == null) {
            return null;
        }
        Double tempC = getDoubleAt(hourly, "temperature_2m", noonIndex);
        Double rhPct = getDoubleAt(hourly, "relative_humidity_2m", noonIndex);
        Double windKmh = getDoubleAt(hourly, "wind_speed_10m", noonIndex);
        if (tempC == null || rhPct == null || windKmh == null) {
            return null;
        }
        Double precipMm = findDailyValue(daily, "precipitation_sum", day.toString());
        return new FwiInputs(tempC, rhPct, windKmh, precipMm == null ? 0.0 : precipMm, day.getMonthValue());
    }

    private Integer findHourlyIndexForTimestamp(JsonNode hourly, String timestamp) {
        JsonNode times = hourly.path("time");
        if (!times.isArray()) {
            return null;
        }
        for (int i = 0; i < times.size(); i++) {
            if (timestamp.equals(times.get(i).asText())) {
                return i;
            }
        }
        return null;
    }

    private Double findDailyValue(JsonNode daily, String field, String dateText) {
        JsonNode times = daily.path("time");
        JsonNode values = daily.path(field);
        if (!times.isArray() || !values.isArray()) {
            return null;
        }
        for (int i = 0; i < times.size(); i++) {
            if (dateText.equals(times.get(i).asText())) {
                return values.get(i).isNull() ? null : values.get(i).asDouble();
            }
        }
        return null;
    }

    private Double getDoubleAt(JsonNode hourly, String field, int index) {
        JsonNode arr = hourly.path(field);
        if (!arr.isArray() || index >= arr.size() || arr.get(index).isNull()) {
            return null;
        }
        return arr.get(index).asDouble();
    }
}
