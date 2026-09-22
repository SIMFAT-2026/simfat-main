package com.simfat.backend.service.fwi;

import com.simfat.backend.model.ComunaFwiState;
import com.simfat.backend.repository.ComunaFwiStateRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Persistence + idempotent-by-calendar-day advance orchestration for the Canadian FWI chain
 * (S1d1, design decision D4). Owns {@code comuna_fwi_state} and decides, given a comuna, a
 * target calendar date and today's noon {@link FwiInputs}, how to call {@link
 * CanadianFwiCalculator#advance} without ever advancing the chain more than once per calendar
 * day — see {@link #advance} for the five cases this handles.
 *
 * <p><b>Known TODO (not a bug in this slice):</b> the calculator is wired via {@link
 * CanadianFwiCalculator#northernHemisphere()} by default. Chile needs southern-hemisphere
 * day-length tables eventually; S1c already made the tables constructor-injectable for exactly
 * this reason (see {@code CanadianFwiCalculator}'s two-arg constructor), and using the northern
 * default here is consistent with S1c's own stated scope (no southern-hemisphere reference
 * values were verified/in-hand), not a new mistake introduced by this slice.
 *
 * <p><b>Deliberately out of scope for S1d1</b> (design D4 describes all of these; they are
 * deferred, not silently dropped):
 * <ul>
 *   <li>Season-start rule (3 consecutive days of noon temp &gt; 12&deg;C) and its {@code
 *   seasonStartedOn} state — this slice always uses the published startup values (FFMC=85,
 *   DMC=6, DC=15) for a cold start or a post-gap restart, with no season-awareness.
 *   <li>DC overwintering (Qf/Qs formula, {@code lastOverwinteredOn}) — not implemented.
 *   <li>Multi-day gap replay via the Open-Meteo Archive — that is S1e's spin-up runner. This
 *   slice's gap policy (see {@link #advance}) is deliberately the SIMPLER, more honest option:
 *   a gap of exactly 1 day is the normal case; a gap beyond {@code maxGapDays} restarts from
 *   startup values; anything in between throws, because fabricating a multi-day replay from a
 *   single day's {@link FwiInputs} would not be a real replay.
 * </ul>
 */
@Service
public class ComunaFwiStateService {

    /** Van Wagner &amp; Pickett (1985) published startup values. */
    static final double STARTUP_FFMC = 85.0;

    static final double STARTUP_DMC = 6.0;
    static final double STARTUP_DC = 15.0;

    static final String METHOD_VAN_WAGNER = "VAN_WAGNER";

    /** First-ever state for a comuna: chain started cold from published startup values. */
    static final String QUALITY_FLAG_WARMUP = "FWI_WARMUP";

    /** A gap beyond {@code maxGapDays} forced a restart from published startup values. */
    static final String QUALITY_FLAG_RESTARTED = "FWI_RESTARTED";

    static final int DEFAULT_MAX_GAP_DAYS = 3;

    private final ComunaFwiStateRepository repository;
    private final CanadianFwiCalculator calculator;
    private final int maxGapDays;

    public ComunaFwiStateService(ComunaFwiStateRepository repository) {
        this(repository, CanadianFwiCalculator.northernHemisphere(), DEFAULT_MAX_GAP_DAYS);
    }

    ComunaFwiStateService(ComunaFwiStateRepository repository, CanadianFwiCalculator calculator, int maxGapDays) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        if (maxGapDays < 1) {
            throw new IllegalArgumentException("maxGapDays must be >= 1, got " + maxGapDays);
        }
        this.maxGapDays = maxGapDays;
    }

    /**
     * Advances (or starts) the FWI chain for one comuna up to {@code targetDate}, persists the
     * new state and returns the resulting outputs. Never calls the calculator more than once.
     *
     * <p>Handles exactly five cases:
     * <ol>
     *   <li>No prior state at all &rarr; cold start from published startup values,
     *   {@code qualityFlag = FWI_WARMUP}.
     *   <li>{@code targetDate == stateDate} (a same-calendar-day re-sync, e.g. the cron's second
     *   daily fire) &rarr; recompute FROM the stored {@code base*} (yesterday's end-of-day
     *   state), NOT from the currently-stored {@code stateDate} values. This is what prevents
     *   the chain from compounding twice a day.
     *   <li>{@code targetDate == stateDate + 1} (the normal daily case) &rarr; today's current
     *   ffmc/dmc/dc become the new {@code base*}, then the chain advances once more.
     *   <li>{@code targetDate > stateDate + 1} (a gap) &rarr; if the gap exceeds {@code
     *   maxGapDays} (default 3), restart from startup values, {@code qualityFlag =
     *   FWI_RESTARTED}; otherwise this slice does not support it (see class javadoc) and
     *   throws {@link UnsupportedOperationException}.
     *   <li>{@code targetDate < stateDate} (moving backwards) &rarr; rejected with {@link
     *   IllegalArgumentException}.
     * </ol>
     */
    public ComunaFwiAdvanceResult advance(String comunaId, LocalDate targetDate, FwiInputs todayNoon) {
        Objects.requireNonNull(comunaId, "comunaId");
        Objects.requireNonNull(targetDate, "targetDate");
        Objects.requireNonNull(todayNoon, "todayNoon");

        Optional<ComunaFwiState> existing = repository.findById(comunaId);
        if (existing.isEmpty()) {
            return coldStart(comunaId, targetDate, todayNoon, QUALITY_FLAG_WARMUP);
        }

        ComunaFwiState state = existing.get();
        LocalDate stateDate = state.getStateDate();

        if (targetDate.isBefore(stateDate)) {
            throw new IllegalArgumentException(
                    "targetDate "
                            + targetDate
                            + " is before the stored stateDate "
                            + stateDate
                            + " for comuna "
                            + comunaId
                            + " -- moving the FWI chain backwards is not supported");
        }

        if (targetDate.isEqual(stateDate)) {
            return sameDayResync(state, todayNoon);
        }

        long gapDays = ChronoUnit.DAYS.between(stateDate, targetDate);
        if (gapDays == 1) {
            return normalDailyAdvance(state, targetDate, todayNoon);
        }

        if (gapDays > maxGapDays) {
            return coldStart(comunaId, targetDate, todayNoon, QUALITY_FLAG_RESTARTED);
        }

        throw new UnsupportedOperationException(
                "Gap of "
                        + gapDays
                        + " day(s) for comuna "
                        + comunaId
                        + " (stateDate="
                        + stateDate
                        + ", targetDate="
                        + targetDate
                        + ") is not supported by S1d1: this slice only advances exactly one"
                        + " calendar day at a time, or restarts from startup values once the gap"
                        + " exceeds maxGapDays="
                        + maxGapDays
                        + ". Filling a smaller gap requires FwiInputs for every intermediate day"
                        + " (a real day-by-day replay), which is out of scope here -- see S1e's"
                        + " spin-up runner for multi-day backfill.");
    }

    private ComunaFwiAdvanceResult coldStart(
            String comunaId, LocalDate targetDate, FwiInputs todayNoon, String qualityFlag) {
        FwiState startupBase = new FwiState(STARTUP_FFMC, STARTUP_DMC, STARTUP_DC);
        FwiOutputs outputs = calculator.advance(startupBase, todayNoon);

        ComunaFwiState state = new ComunaFwiState();
        state.setId(comunaId);
        state.setStateDate(targetDate);
        state.setFfmc(outputs.ffmc());
        state.setDmc(outputs.dmc());
        state.setDc(outputs.dc());
        state.setBaseDate(targetDate.minusDays(1));
        state.setBaseFfmc(STARTUP_FFMC);
        state.setBaseDmc(STARTUP_DMC);
        state.setBaseDc(STARTUP_DC);
        state.setMethod(METHOD_VAN_WAGNER);
        state.setQualityFlag(qualityFlag);
        state.setUpdatedAt(LocalDateTime.now());
        repository.save(state);

        return new ComunaFwiAdvanceResult(outputs, qualityFlag);
    }

    private ComunaFwiAdvanceResult sameDayResync(ComunaFwiState state, FwiInputs todayNoon) {
        FwiState base = new FwiState(state.getBaseFfmc(), state.getBaseDmc(), state.getBaseDc());
        FwiOutputs outputs = calculator.advance(base, todayNoon);

        // Preserve a warmup/restart flag set earlier the SAME calendar day (e.g. the cron's
        // first daily fire went through coldStart): a same-day resync must not silently discard
        // it. Only a genuinely NEW calendar day (normalDailyAdvance) clears a stale flag.
        String preservedQualityFlag = state.getQualityFlag();

        state.setFfmc(outputs.ffmc());
        state.setDmc(outputs.dmc());
        state.setDc(outputs.dc());
        state.setQualityFlag(preservedQualityFlag);
        state.setUpdatedAt(LocalDateTime.now());
        repository.save(state);

        return new ComunaFwiAdvanceResult(outputs, preservedQualityFlag);
    }

    private ComunaFwiAdvanceResult normalDailyAdvance(
            ComunaFwiState state, LocalDate targetDate, FwiInputs todayNoon) {
        FwiState newBase = new FwiState(state.getFfmc(), state.getDmc(), state.getDc());
        FwiOutputs outputs = calculator.advance(newBase, todayNoon);

        state.setBaseDate(state.getStateDate());
        state.setBaseFfmc(state.getFfmc());
        state.setBaseDmc(state.getDmc());
        state.setBaseDc(state.getDc());
        state.setStateDate(targetDate);
        state.setFfmc(outputs.ffmc());
        state.setDmc(outputs.dmc());
        state.setDc(outputs.dc());
        state.setQualityFlag(null);
        state.setUpdatedAt(LocalDateTime.now());
        repository.save(state);

        return new ComunaFwiAdvanceResult(outputs, null);
    }
}
