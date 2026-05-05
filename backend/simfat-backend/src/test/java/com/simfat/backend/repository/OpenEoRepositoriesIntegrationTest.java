package com.simfat.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.simfat.backend.model.DashboardRegionSnapshot;
import com.simfat.backend.model.IndicatorType;
import com.simfat.backend.model.OpenEoIndicatorObservation;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.TestPropertySource;

@DataMongoTest
@TestPropertySource(properties = "spring.data.mongodb.auto-index-creation=true")
class OpenEoRepositoriesIntegrationTest {

    @Autowired
    private OpenEoIndicatorObservationRepository observationRepository;
    @Autowired
    private DashboardRegionSnapshotRepository snapshotRepository;

    @BeforeEach
    void cleanCollections() {
        observationRepository.deleteAll();
        snapshotRepository.deleteAll();
    }

    @Test
    void observationRepository_enforcesUniqueRegionIndicatorObservedAt() {
        LocalDateTime observedAt = LocalDateTime.parse("2026-03-01T00:00:00");

        OpenEoIndicatorObservation first = new OpenEoIndicatorObservation();
        first.setRegionId("region-1");
        first.setIndicator(IndicatorType.NDVI);
        first.setObservedAt(observedAt);
        first.setValue(0.5);
        observationRepository.save(first);

        OpenEoIndicatorObservation duplicate = new OpenEoIndicatorObservation();
        duplicate.setRegionId("region-1");
        duplicate.setIndicator(IndicatorType.NDVI);
        duplicate.setObservedAt(observedAt);
        duplicate.setValue(0.8);

        assertThrows(DuplicateKeyException.class, () -> observationRepository.save(duplicate));
    }

    @Test
    void snapshotRepository_enforcesUniqueRegionId() {
        DashboardRegionSnapshot first = new DashboardRegionSnapshot();
        first.setRegionId("region-2");
        first.setComputedAt(LocalDateTime.now());
        snapshotRepository.save(first);

        DashboardRegionSnapshot duplicate = new DashboardRegionSnapshot();
        duplicate.setRegionId("region-2");
        duplicate.setComputedAt(LocalDateTime.now());

        assertThrows(DuplicateKeyException.class, () -> snapshotRepository.save(duplicate));
        assertEquals(1, snapshotRepository.count());
    }
}
