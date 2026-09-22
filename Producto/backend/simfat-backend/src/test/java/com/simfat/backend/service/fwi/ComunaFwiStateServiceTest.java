package com.simfat.backend.service.fwi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.simfat.backend.model.ComunaFwiState;
import com.simfat.backend.repository.ComunaFwiStateRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ComunaFwiStateService} — the {@code comuna_fwi_state} persistence +
 * idempotent-by-calendar-day advance orchestration (S1d1). Pure logic tests: the repository is
 * mocked, no MongoDB required (see {@code ComunaFwiStateRepositoryIntegrationTest} for the
 * Mongo-backed persistence proof).
 *
 * <p>The single most important test in this class is {@link
 * #sameDayTwice_producesIdenticalState_notCompounded()} — it proves the "twice-a-day trap"
 * (design D4) does not happen: the daily cron fires twice per calendar day, and the Van Wagner
 * chain must advance EXACTLY ONCE per day, not twice. A companion diagnostic test, {@link
 * #sameDayTwice_ifNaivelyReadvancedFromCurrentState_wouldCompound_soThatIsNotWhatWeDo()},
 * demonstrates numerically what the NAIVE (buggy) implementation would produce if it advanced
 * from the currently-stored (already-advanced) state instead of from {@code base*} — and asserts
 * the real service's result differs from it, i.e. the real implementation is NOT doing the naive
 * thing.
 */
@ExtendWith(MockitoExtension.class)
class ComunaFwiStateServiceTest {

    private static final String COMUNA_ID = "CHL.8.1.1_1";
    private static final CanadianFwiCalculator CALCULATOR = CanadianFwiCalculator.northernHemisphere();

    @Mock private ComunaFwiStateRepository repository;

    private ComunaFwiStateService service;

    @BeforeEach
    void setUp() {
        service = new ComunaFwiStateService(repository, CALCULATOR, 3);
    }

    private static FwiInputs someInputs() {
        return new FwiInputs(17.0, 42.0, 6.0, 0.0, 1);
    }

    private static FwiInputs otherInputs() {
        return new FwiInputs(24.0, 30.0, 12.0, 0.0, 1);
    }

    // -- Case 1: no prior state (cold start) -----------------------------------------------

    @Test
    void noPriorState_startsFromPublishedStartupValuesAndFlagsWarmup() {
        when(repository.findById(COMUNA_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        LocalDate targetDate = LocalDate.of(2026, 1, 1);
        FwiInputs inputs = someInputs();

        ComunaFwiAdvanceResult result = service.advance(COMUNA_ID, targetDate, inputs);

        FwiOutputs expected = CALCULATOR.advance(new FwiState(85.0, 6.0, 15.0), inputs);
        assertEquals(expected.ffmc(), result.outputs().ffmc(), 1e-9);
        assertEquals(expected.dmc(), result.outputs().dmc(), 1e-9);
        assertEquals(expected.dc(), result.outputs().dc(), 1e-9);
        assertEquals(expected.fwi(), result.outputs().fwi(), 1e-9);
        assertEquals("FWI_WARMUP", result.qualityFlag());

        ArgumentCaptor<ComunaFwiState> saved = ArgumentCaptor.forClass(ComunaFwiState.class);
        verify(repository).save(saved.capture());
        ComunaFwiState state = saved.getValue();
        assertEquals(COMUNA_ID, state.getId());
        assertEquals(targetDate, state.getStateDate());
        assertEquals(targetDate.minusDays(1), state.getBaseDate());
        assertEquals(85.0, state.getBaseFfmc(), 1e-9);
        assertEquals(6.0, state.getBaseDmc(), 1e-9);
        assertEquals(15.0, state.getBaseDc(), 1e-9);
        assertEquals(expected.ffmc(), state.getFfmc(), 1e-9);
        assertEquals("VAN_WAGNER", state.getMethod());
    }

    // -- Case 2: normal daily advance -------------------------------------------------------

    @Test
    void normalDailyAdvance_usesYesterdaysCurrentStateAsTodaysBase() {
        LocalDate yesterday = LocalDate.of(2026, 1, 5);
        LocalDate today = yesterday.plusDays(1);
        ComunaFwiState existing = existingState(yesterday, 88.0, 20.0, 100.0, yesterday.minusDays(1), 87.0, 18.0, 95.0);
        when(repository.findById(COMUNA_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        FwiInputs inputs = someInputs();

        ComunaFwiAdvanceResult result = service.advance(COMUNA_ID, today, inputs);

        FwiOutputs expected = CALCULATOR.advance(new FwiState(88.0, 20.0, 100.0), inputs);
        assertEquals(expected.ffmc(), result.outputs().ffmc(), 1e-9);
        assertNull(result.qualityFlag());

        ArgumentCaptor<ComunaFwiState> saved = ArgumentCaptor.forClass(ComunaFwiState.class);
        verify(repository).save(saved.capture());
        ComunaFwiState state = saved.getValue();
        assertEquals(today, state.getStateDate());
        assertEquals(yesterday, state.getBaseDate());
        assertEquals(88.0, state.getBaseFfmc(), 1e-9);
        assertEquals(20.0, state.getBaseDmc(), 1e-9);
        assertEquals(100.0, state.getBaseDc(), 1e-9);
        assertEquals(expected.ffmc(), state.getFfmc(), 1e-9);
    }

    // -- Case 3: THE critical same-day-twice idempotency case --------------------------------

    @Test
    void sameDayTwice_producesIdenticalState_notCompounded() {
        LocalDate today = LocalDate.of(2026, 1, 10);
        LocalDate yesterday = today.minusDays(1);
        FwiInputs inputs = someInputs();

        // First call of the day (e.g. the 01:30 forecast-noon run): no prior state yet, so this
        // is also a cold start for this fixture -- but the interesting part is the SECOND call.
        ComunaFwiState afterFirstCall = existingState(today, 0, 0, 0, yesterday, 85.0, 6.0, 15.0);
        // First call: findById returns empty, we cold-start.
        when(repository.findById(COMUNA_ID)).thenReturn(Optional.empty(), Optional.of(afterFirstCall));
        ArgumentCaptor<ComunaFwiState> savedFirst = ArgumentCaptor.forClass(ComunaFwiState.class);
        when(repository.save(savedFirst.capture())).thenAnswer(inv -> inv.getArgument(0));

        ComunaFwiAdvanceResult firstResult = service.advance(COMUNA_ID, today, inputs);
        // Mirror what was actually persisted so the second findById() reflects call #1's save.
        ComunaFwiState persistedAfterFirst = savedFirst.getValue();

        when(repository.findById(COMUNA_ID)).thenReturn(Optional.of(persistedAfterFirst));
        ArgumentCaptor<ComunaFwiState> savedSecond = ArgumentCaptor.forClass(ComunaFwiState.class);
        when(repository.save(savedSecond.capture())).thenAnswer(inv -> inv.getArgument(0));

        // Second call of the day (e.g. the 13:30 observed-noon run), SAME inputs.
        ComunaFwiAdvanceResult secondResult = service.advance(COMUNA_ID, today, inputs);

        assertEquals(firstResult.outputs().ffmc(), secondResult.outputs().ffmc(), 1e-12,
            "two advance-attempts on the same calendar day with the same inputs must produce the SAME ffmc");
        assertEquals(firstResult.outputs().dmc(), secondResult.outputs().dmc(), 1e-12);
        assertEquals(firstResult.outputs().dc(), secondResult.outputs().dc(), 1e-12);
        assertEquals(firstResult.outputs().fwi(), secondResult.outputs().fwi(), 1e-12);

        ComunaFwiState stateAfterSecond = savedSecond.getValue();
        assertEquals(today, stateAfterSecond.getStateDate());
        assertEquals(persistedAfterFirst.getBaseFfmc(), stateAfterSecond.getBaseFfmc(), 1e-12,
            "base* must NOT change on a same-day re-sync");
        assertEquals(persistedAfterFirst.getBaseDmc(), stateAfterSecond.getBaseDmc(), 1e-12);
        assertEquals(persistedAfterFirst.getBaseDc(), stateAfterSecond.getBaseDc(), 1e-12);
    }

    /**
     * Diagnostic proof, not a behavior spec: shows what a NAIVE (buggy) same-day re-sync would
     * produce if it advanced from the CURRENTLY STORED (already-advanced) ffmc/dmc/dc instead of
     * from {@code base*} — i.e. {@code advance(advance(base, inputs1), inputs2)} instead of
     * {@code advance(base, inputs2)}. If {@link ComunaFwiStateService} regressed to this naive
     * behavior, {@link #sameDayTwice_producesIdenticalState_notCompounded()} would still pass
     * when both calls use identical inputs (compounding an idempotent function is still
     * idempotent) — so THIS test is the one that would actually catch that regression: it uses
     * two DIFFERENT same-day inputs and asserts the real service's second result matches
     * "advance from base with the second day's inputs", NOT the naive double-advance.
     */
    @Test
    void sameDayTwice_ifNaivelyReadvancedFromCurrentState_wouldCompound_soThatIsNotWhatWeDo() {
        LocalDate today = LocalDate.of(2026, 1, 10);
        LocalDate yesterday = today.minusDays(1);
        FwiState base = new FwiState(85.0, 6.0, 15.0);
        FwiInputs firstCallInputs = someInputs();
        FwiInputs secondCallInputs = otherInputs();

        ComunaFwiState existing = existingState(today, /* placeholder, overwritten below */
            0, 0, 0, yesterday, base.ffmc(), base.dmc(), base.dc());
        // Simulate "already advanced once today" using firstCallInputs, as call #1 would leave it.
        FwiOutputs afterFirstCall = CALCULATOR.advance(base, firstCallInputs);
        existing.setFfmc(afterFirstCall.ffmc());
        existing.setDmc(afterFirstCall.dmc());
        existing.setDc(afterFirstCall.dc());

        when(repository.findById(COMUNA_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ComunaFwiAdvanceResult secondCallResult = service.advance(COMUNA_ID, today, secondCallInputs);

        // The NAIVE/buggy result: re-advancing from the already-advanced current state.
        FwiOutputs naiveCompoundedResult = CALCULATOR.advance(
            new FwiState(afterFirstCall.ffmc(), afterFirstCall.dmc(), afterFirstCall.dc()), secondCallInputs);
        // The CORRECT result: advancing from base (yesterday's end-of-day state) with the
        // second call's (latest) inputs.
        FwiOutputs correctResult = CALCULATOR.advance(base, secondCallInputs);

        assertNotEquals(naiveCompoundedResult.ffmc(), secondCallResult.outputs().ffmc(),
            "the real service must NOT compound: its result must differ from the naive double-advance");
        assertEquals(correctResult.ffmc(), secondCallResult.outputs().ffmc(), 1e-12,
            "the real service must derive today's value from BASE + the LATEST inputs, discarding the first call's already-advanced value");
    }

    // -- Case 4: gap handling -----------------------------------------------------------------

    @Test
    void gapWithinThreshold_isUnsupportedByThisSlice() {
        LocalDate stateDate = LocalDate.of(2026, 2, 1);
        LocalDate targetDate = stateDate.plusDays(2); // gap = 2, <= maxGapDays(3), > 1
        ComunaFwiState existing = existingState(stateDate, 90.0, 25.0, 120.0, stateDate.minusDays(1), 89.0, 24.0, 118.0);
        when(repository.findById(COMUNA_ID)).thenReturn(Optional.of(existing));

        assertThrows(UnsupportedOperationException.class,
            () -> service.advance(COMUNA_ID, targetDate, someInputs()));
        verify(repository, never()).save(any());
    }

    @Test
    void gapExceedingThreshold_restartsFromStartupValuesAndFlagsRestarted() {
        LocalDate stateDate = LocalDate.of(2026, 2, 1);
        LocalDate targetDate = stateDate.plusDays(4); // gap = 4 > maxGapDays(3)
        ComunaFwiState existing = existingState(stateDate, 90.0, 25.0, 120.0, stateDate.minusDays(1), 89.0, 24.0, 118.0);
        when(repository.findById(COMUNA_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        FwiInputs inputs = someInputs();

        ComunaFwiAdvanceResult result = service.advance(COMUNA_ID, targetDate, inputs);

        FwiOutputs expected = CALCULATOR.advance(new FwiState(85.0, 6.0, 15.0), inputs);
        assertEquals(expected.ffmc(), result.outputs().ffmc(), 1e-9);
        assertEquals("FWI_RESTARTED", result.qualityFlag());

        ArgumentCaptor<ComunaFwiState> saved = ArgumentCaptor.forClass(ComunaFwiState.class);
        verify(repository).save(saved.capture());
        assertEquals(targetDate, saved.getValue().getStateDate());
        assertEquals(targetDate.minusDays(1), saved.getValue().getBaseDate());
    }

    @Test
    void gapEqualsMaxGapDays_isStillUnsupportedByThisSlice() {
        LocalDate stateDate = LocalDate.of(2026, 5, 1);
        LocalDate targetDate = stateDate.plusDays(3); // gap == maxGapDays(3): the reject/restart boundary
        ComunaFwiState existing = existingState(stateDate, 90.0, 25.0, 120.0, stateDate.minusDays(1), 89.0, 24.0, 118.0);
        when(repository.findById(COMUNA_ID)).thenReturn(Optional.of(existing));

        assertThrows(UnsupportedOperationException.class,
            () -> service.advance(COMUNA_ID, targetDate, someInputs()));
        verify(repository, never()).save(any());
    }

    // -- Case 5: backwards date rejection -------------------------------------------------------

    @Test
    void targetDateBeforeStateDate_rejectsWithClearException() {
        LocalDate stateDate = LocalDate.of(2026, 3, 10);
        LocalDate targetDate = stateDate.minusDays(1);
        ComunaFwiState existing = existingState(stateDate, 90.0, 25.0, 120.0, stateDate.minusDays(1), 89.0, 24.0, 118.0);
        when(repository.findById(COMUNA_ID)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class,
            () -> service.advance(COMUNA_ID, targetDate, someInputs()));
        verify(repository, never()).save(any());
        verify(repository, times(1)).findById(COMUNA_ID);
    }

    // -- Same-day resync must PRESERVE an existing warmup/restart flag ----------------------

    @Test
    void sameDayResync_preservesWarmupFlagFromEarlierColdStartOnSameDay() {
        LocalDate today = LocalDate.of(2026, 1, 20);
        FwiInputs firstInputs = someInputs();
        FwiInputs secondInputs = otherInputs();

        // First call of the day: no prior state -> cold start, flags FWI_WARMUP.
        when(repository.findById(COMUNA_ID)).thenReturn(Optional.empty());
        ArgumentCaptor<ComunaFwiState> savedFirst = ArgumentCaptor.forClass(ComunaFwiState.class);
        when(repository.save(savedFirst.capture())).thenAnswer(inv -> inv.getArgument(0));

        ComunaFwiAdvanceResult firstResult = service.advance(COMUNA_ID, today, firstInputs);
        assertEquals("FWI_WARMUP", firstResult.qualityFlag());
        ComunaFwiState persistedAfterFirst = savedFirst.getValue();

        // Second call, SAME calendar day, different inputs (a same-day resync, e.g. the cron's
        // second daily fire).
        when(repository.findById(COMUNA_ID)).thenReturn(Optional.of(persistedAfterFirst));
        ArgumentCaptor<ComunaFwiState> savedSecond = ArgumentCaptor.forClass(ComunaFwiState.class);
        when(repository.save(savedSecond.capture())).thenAnswer(inv -> inv.getArgument(0));

        ComunaFwiAdvanceResult secondResult = service.advance(COMUNA_ID, today, secondInputs);

        assertEquals(
                "FWI_WARMUP",
                secondResult.qualityFlag(),
                "a same-day resync must PRESERVE an existing warmup/restart flag set earlier the"
                        + " same day, not silently discard it");
        assertEquals("FWI_WARMUP", savedSecond.getValue().getQualityFlag());
    }

    @Test
    void sameDayResync_keepsQualityFlagNullWhenNoneWasSet() {
        LocalDate today = LocalDate.of(2026, 1, 21);
        LocalDate yesterday = today.minusDays(1);
        // Existing state for TODAY already, with no warmup/restart flag (a normal day).
        ComunaFwiState existing =
                existingState(today, 88.0, 20.0, 100.0, yesterday, 87.0, 18.0, 95.0);
        existing.setQualityFlag(null);
        when(repository.findById(COMUNA_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ComunaFwiAdvanceResult result = service.advance(COMUNA_ID, today, otherInputs());

        assertNull(
                result.qualityFlag(),
                "a same-day resync of an already-normal state must remain unflagged, not invent a"
                        + " flag that wasn't there");
    }

    private static ComunaFwiState existingState(
            LocalDate stateDate, double ffmc, double dmc, double dc,
            LocalDate baseDate, double baseFfmc, double baseDmc, double baseDc) {
        ComunaFwiState state = new ComunaFwiState();
        state.setId(COMUNA_ID);
        state.setStateDate(stateDate);
        state.setFfmc(ffmc);
        state.setDmc(dmc);
        state.setDc(dc);
        state.setBaseDate(baseDate);
        state.setBaseFfmc(baseFfmc);
        state.setBaseDmc(baseDmc);
        state.setBaseDc(baseDc);
        state.setMethod("VAN_WAGNER");
        return state;
    }
}
