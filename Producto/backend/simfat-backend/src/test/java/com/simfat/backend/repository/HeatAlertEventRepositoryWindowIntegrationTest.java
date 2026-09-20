package com.simfat.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.RiskLevel;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.PageRequest;

// Requires a real MongoDB (same setup as the other @DataMongoTest classes); not runnable without it.
@DataMongoTest
class HeatAlertEventRepositoryWindowIntegrationTest {

    @Autowired
    private HeatAlertEventRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private void save(String id, String regionId, LocalDateTime when) {
        HeatAlertEvent e = new HeatAlertEvent();
        e.setId(id);
        e.setRegionId(regionId);
        e.setFechaEvento(when);
        e.setNivelRiesgo(RiskLevel.ALTO);
        repository.save(e);
    }

    @Test
    void windowIsHalfOpenInclusiveStartExclusiveEndNewestFirst() {
        LocalDateTime from = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime endExclusive = LocalDateTime.of(2026, 3, 6, 0, 0);
        save("at-start", "biobio", from);
        save("before-start", "biobio", from.minusNanos(1_000_000));
        save("mid", "biobio", LocalDateTime.of(2026, 3, 3, 12, 0));
        save("last-instant", "biobio", endExclusive.minusNanos(1_000_000));
        save("at-end", "biobio", endExclusive);
        save("other-region", "maule", LocalDateTime.of(2026, 3, 3, 12, 0));

        List<HeatAlertEvent> result = repository.findByRegionIdInWindowNewestFirst(
            "biobio", from, endExclusive, PageRequest.of(0, 10));

        assertThat(result).extracting(HeatAlertEvent::getId).containsExactly("last-instant", "mid", "at-start");
    }

    @Test
    void pageSizeIsAHardCap() {
        LocalDateTime from = LocalDateTime.of(2026, 3, 1, 0, 0);
        for (int i = 0; i < 5; i++) {
            save("e" + i, "biobio", from.plusHours(i));
        }

        List<HeatAlertEvent> result = repository.findByRegionIdInWindowNewestFirst(
            "biobio", from, from.plusDays(1), PageRequest.of(0, 2));

        assertThat(result).extracting(HeatAlertEvent::getId).containsExactly("e4", "e3");
    }
}
