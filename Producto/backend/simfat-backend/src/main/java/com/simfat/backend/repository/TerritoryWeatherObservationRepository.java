package com.simfat.backend.repository;

import com.simfat.backend.model.TerritoryWeatherObservation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TerritoryWeatherObservationRepository extends MongoRepository<TerritoryWeatherObservation, String> {

    Optional<TerritoryWeatherObservation> findTopByRegionIdOrderByObservedAtDesc(String regionId);

    List<TerritoryWeatherObservation> findByRegionIdAndObservedAtBetweenOrderByObservedAtDesc(
        String regionId, LocalDateTime from, LocalDateTime to
    );

    List<TerritoryWeatherObservation> findByRegionIdInOrderByObservedAtDesc(List<String> regionIds);
}
