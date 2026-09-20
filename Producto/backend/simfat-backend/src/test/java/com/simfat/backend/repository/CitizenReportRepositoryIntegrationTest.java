package com.simfat.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.simfat.backend.model.CitizenReport;
import com.simfat.backend.model.CitizenReportStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.PageRequest;

// Requires a real MongoDB (same setup as the other @DataMongoTest classes).
@DataMongoTest
class CitizenReportRepositoryIntegrationTest {

    @Autowired
    private CitizenReportRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private CitizenReport save(String id, String regionId, CitizenReportStatus status, LocalDateTime createdAt) {
        CitizenReport r = new CitizenReport();
        r.setId(id);
        r.setRegionId(regionId);
        r.setStatus(status);
        r.setCreatedAt(createdAt);
        return repository.save(r);
    }

    @Test
    void windowQueryFiltersByRegionStatusAndDate() {
        LocalDateTime now = LocalDateTime.of(2026, 3, 10, 12, 0);
        save("ok", "biobio", CitizenReportStatus.VALIDADO, now);
        save("received", "biobio", CitizenReportStatus.RECIBIDO, now);
        save("discarded", "biobio", CitizenReportStatus.DESCARTADO, now);
        save("other-region", "maule", CitizenReportStatus.VALIDADO, now);
        save("too-old", "biobio", CitizenReportStatus.VALIDADO, now.minusDays(30));

        List<CitizenReport> result = repository.findByRegionIdAndStatusInWindowNewestFirst(
            "biobio", CitizenReportStatus.VALIDADO, now.minusDays(7), now.plusDays(1), PageRequest.of(0, 10));

        assertThat(result).extracting(CitizenReport::getId).containsExactly("ok");
    }

    @Test
    void windowIsHalfOpenInclusiveStartExclusiveEnd() {
        LocalDateTime from = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime endExclusive = LocalDateTime.of(2026, 3, 6, 0, 0);
        save("at-start", "biobio", CitizenReportStatus.VALIDADO, from);
        save("before-start", "biobio", CitizenReportStatus.VALIDADO, from.minusNanos(1_000_000));
        save("last-instant", "biobio", CitizenReportStatus.VALIDADO, endExclusive.minusNanos(1_000_000));
        save("late-evening", "biobio", CitizenReportStatus.VALIDADO, LocalDateTime.of(2026, 3, 5, 23, 59, 59, 500_000_000));
        save("at-end", "biobio", CitizenReportStatus.VALIDADO, endExclusive);

        List<CitizenReport> regional = repository.findByRegionIdAndStatusInWindowNewestFirst(
            "biobio", CitizenReportStatus.VALIDADO, from, endExclusive, PageRequest.of(0, 10));
        List<CitizenReport> national = repository.findByStatusInWindowNewestFirst(
            CitizenReportStatus.VALIDADO, from, endExclusive, PageRequest.of(0, 10));

        assertThat(regional).extracting(CitizenReport::getId)
            .containsExactlyInAnyOrder("at-start", "last-instant", "late-evening");
        assertThat(national).extracting(CitizenReport::getId)
            .containsExactlyInAnyOrder("at-start", "last-instant", "late-evening");
    }

    @Test
    void boundedQueriesReturnNewestFirstAndHonourThePageSizeCap() {
        LocalDateTime now = LocalDateTime.of(2026, 3, 10, 12, 0);
        save("oldest", "biobio", CitizenReportStatus.VALIDADO, now.minusDays(3));
        save("newest", "biobio", CitizenReportStatus.VALIDADO, now);
        save("middle", "maule", CitizenReportStatus.VALIDADO, now.minusDays(1));
        save("hidden", "biobio", CitizenReportStatus.RECIBIDO, now);
        LocalDateTime from = now.minusDays(7);
        LocalDateTime to = now.plusDays(1);

        List<CitizenReport> national = repository.findByStatusInWindowNewestFirst(
            CitizenReportStatus.VALIDADO, from, to, PageRequest.of(0, 2));
        List<CitizenReport> regional = repository.findByRegionIdAndStatusInWindowNewestFirst(
            "biobio", CitizenReportStatus.VALIDADO, from, to, PageRequest.of(0, 10));

        assertThat(national).extracting(CitizenReport::getId).containsExactly("newest", "middle");
        assertThat(regional).extracting(CitizenReport::getId).containsExactly("newest", "oldest");
    }
}
