package com.simfat.backend.repository;

import com.simfat.backend.model.TerritoryWeatherObservation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TerritoryWeatherObservationRepository extends MongoRepository<TerritoryWeatherObservation, String> {

    Optional<TerritoryWeatherObservation> findTopByRegionIdOrderByObservedAtDesc(String regionId);

    List<TerritoryWeatherObservation> findByRegionIdAndObservedAtBetweenOrderByObservedAtDesc(
        String regionId, LocalDateTime from, LocalDateTime to
    );

    // Returns exactly one (the latest) observation per regionId — avoids loading full history.
    @Aggregation(pipeline = {
        "{ '$match': { 'regionId': { '$in': ?0 } } }",
        "{ '$sort': { 'regionId': 1, 'observedAt': -1 } }",
        "{ '$group': { '_id': '$regionId', 'doc': { '$first': '$$ROOT' } } }",
        "{ '$replaceRoot': { 'newRoot': '$doc' } }"
    })
    List<TerritoryWeatherObservation> findLatestPerRegionId(List<String> regionIds);
}
