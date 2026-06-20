package com.simfat.backend.repository;

import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.RiskLevel;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface HeatAlertEventRepository extends MongoRepository<HeatAlertEvent, String> {

    List<HeatAlertEvent> findByRegionId(String regionId);

    List<HeatAlertEvent> findByRegionIdAndFechaEventoBetween(String regionId, LocalDateTime from, LocalDateTime to);

    // Returns the top N most intense NASA_FIRMS hotspots for the given period, sorted by FRP descending.
    // The sort+limit is evaluated server-side to avoid transferring the full result set.
    @Query(value = "{ 'regionId': ?0, 'fuente': 'NASA_FIRMS', 'firmsConfidence': { '$ne': 'l' }, 'fechaEvento': { '$gte': ?1, '$lte': ?2 } }",
           sort  = "{ 'firmsFrp': -1 }")
    List<HeatAlertEvent> findTopFirmsEvents(String regionId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    // Returns non-satellite alerts (CONAF, manual, temperature) for the given period.
    // The $ne filter is applied server-side so only non-FIRMS records are transferred.
    @Query("{ 'regionId': ?0, 'fuente': { '$ne': 'NASA_FIRMS' }, 'fechaEvento': { '$gte': ?1, '$lte': ?2 } }")
    List<HeatAlertEvent> findAlertsEvents(String regionId, LocalDateTime from, LocalDateTime to);

    Long countByRegionIdAndFechaEventoBetween(String regionId, LocalDateTime start, LocalDateTime end);

    Long countByNivelRiesgo(RiskLevel nivelRiesgo);

    boolean existsByRegionIdAndLatitudAndLongitudAndFechaEventoAndFuente(
        String regionId, Double latitud, Double longitud, LocalDateTime fechaEvento, String fuente);
}
