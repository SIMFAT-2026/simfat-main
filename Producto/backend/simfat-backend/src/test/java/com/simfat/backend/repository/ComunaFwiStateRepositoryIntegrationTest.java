package com.simfat.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.simfat.backend.model.ComunaFwiState;
import com.simfat.backend.service.fwi.ComunaFwiAdvanceResult;
import com.simfat.backend.service.fwi.ComunaFwiStateService;
import com.simfat.backend.service.fwi.FwiInputs;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

/**
 * Mongo integration test for {@code comuna_fwi_state} (S1d1). Requires a real MongoDB (same
 * setup as {@code MapbiomasSeedLoaderIntegrationTest}); not runnable without it. Uses the real
 * {@link ComunaFwiStateService} against a real {@link ComunaFwiStateRepository} — the unit tests
 * in {@code ComunaFwiStateServiceTest} cover the orchestration logic in isolation with a mocked
 * repository; this test proves the same behavior survives an actual Mongo round-trip (upsert by
 * {@code _id = comunaId}, field types, no serialization surprises for {@link LocalDate}).
 */
@DataMongoTest
class ComunaFwiStateRepositoryIntegrationTest {

    private static final String COMUNA_ID = "CHL.8.1.1_1-fwi-it";

    @Autowired private ComunaFwiStateRepository repository;

    private ComunaFwiStateService service;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        service = new ComunaFwiStateService(repository);
    }

    private static FwiInputs someInputs() {
        return new FwiInputs(18.0, 40.0, 8.0, 0.0, 1);
    }

    @Test
    void coldStart_persistsOneDocumentKeyedByComunaId() {
        LocalDate targetDate = LocalDate.of(2026, 1, 1);

        service.advance(COMUNA_ID, targetDate, someInputs());

        ComunaFwiState saved = repository.findById(COMUNA_ID).orElseThrow();
        assertThat(saved.getId()).isEqualTo(COMUNA_ID);
        assertThat(saved.getStateDate()).isEqualTo(targetDate);
        assertThat(saved.getQualityFlag()).isEqualTo("FWI_WARMUP");
        assertThat(saved.getMethod()).isEqualTo("VAN_WAGNER");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void reSyncingTheSameCalendarDayTwice_viaRealMongoRoundTrip_doesNotCompound() {
        LocalDate today = LocalDate.of(2026, 1, 10);
        FwiInputs inputs = someInputs();

        ComunaFwiAdvanceResult first = service.advance(COMUNA_ID, today, inputs);
        ComunaFwiAdvanceResult second = service.advance(COMUNA_ID, today, inputs);

        assertThat(second.outputs().ffmc()).isEqualTo(first.outputs().ffmc());
        assertThat(second.outputs().dmc()).isEqualTo(first.outputs().dmc());
        assertThat(second.outputs().dc()).isEqualTo(first.outputs().dc());
        assertThat(repository.count()).isEqualTo(1);

        ComunaFwiState persisted = repository.findById(COMUNA_ID).orElseThrow();
        assertThat(persisted.getStateDate()).isEqualTo(today);
        assertThat(persisted.getBaseDate()).isEqualTo(today.minusDays(1));
    }

    @Test
    void normalDailyAdvance_viaRealMongoRoundTrip_rollsBaseForward() {
        LocalDate day1 = LocalDate.of(2026, 1, 20);
        LocalDate day2 = day1.plusDays(1);

        ComunaFwiAdvanceResult firstDay = service.advance(COMUNA_ID, day1, someInputs());
        service.advance(COMUNA_ID, day2, someInputs());

        ComunaFwiState persisted = repository.findById(COMUNA_ID).orElseThrow();
        assertThat(persisted.getStateDate()).isEqualTo(day2);
        assertThat(persisted.getBaseDate()).isEqualTo(day1);
        assertThat(persisted.getBaseFfmc()).isEqualTo(firstDay.outputs().ffmc());
        assertThat(persisted.getQualityFlag()).isNull();
    }
}
