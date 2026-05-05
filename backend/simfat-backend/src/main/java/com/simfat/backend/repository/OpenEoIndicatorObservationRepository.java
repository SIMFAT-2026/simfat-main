package com.simfat.backend.repository;

import com.simfat.backend.model.IndicatorType;
import com.simfat.backend.model.OpenEoIndicatorObservation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OpenEoIndicatorObservationRepository extends MongoRepository<OpenEoIndicatorObservation, String> {

    Optional<OpenEoIndicatorObservation> findTopByRegionIdAndIndicatorOrderByObservedAtDesc(String regionId, IndicatorType indicator);

    Optional<OpenEoIndicatorObservation> findTopByRegionIdAndIndicatorOrderByIngestedAtDesc(String regionId, IndicatorType indicator);

    List<OpenEoIndicatorObservation> findByRegionIdAndIndicatorAndObservedAtBetweenOrderByObservedAtAsc(
        String regionId,
        IndicatorType indicator,
        LocalDateTime from,
        LocalDateTime to
    );

    List<OpenEoIndicatorObservation> findByIndicatorAndObservedAtBetweenOrderByObservedAtDesc(
        IndicatorType indicator,
        LocalDateTime from,
        LocalDateTime to,
        Pageable pageable
    );

    Optional<OpenEoIndicatorObservation> findByRegionIdAndIndicatorAndObservedAt(
        String regionId,
        IndicatorType indicator,
        LocalDateTime observedAt
    );
}
