package com.simfat.backend.repository;

import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.RiskLevel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface HeatAlertEventRepository extends MongoRepository<HeatAlertEvent, String> {

    List<HeatAlertEvent> findByRegionId(String regionId);

    List<HeatAlertEvent> findByRegionIdAndFechaEventoBetween(String regionId, LocalDateTime from, LocalDateTime to);

    // Returns the most recent NASA_FIRMS hotspots for the given period, sorted by date descending.
    // Sorting by recency (instead of FRP) ensures active detections from large/noisy regions
    // are never pushed out of the page by older, higher-FRP hotspots elsewhere in the bbox.
    // The sort+limit is evaluated server-side to avoid transferring the full result set.
    @Query(value = "{ 'regionId': ?0, 'fuente': 'NASA_FIRMS', 'firmsConfidence': { '$ne': 'l' }, 'fechaEvento': { '$gte': ?1, '$lte': ?2 } }",
           sort  = "{ 'fechaEvento': -1 }")
    List<HeatAlertEvent> findTopFirmsEvents(String regionId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    // Returns non-satellite alerts (CONAF, manual, temperature) for the given period.
    // The $ne filter is applied server-side so only non-FIRMS records are transferred.
    @Query("{ 'regionId': ?0, 'fuente': { '$ne': 'NASA_FIRMS' }, 'fechaEvento': { '$gte': ?1, '$lte': ?2 } }")
    List<HeatAlertEvent> findAlertsEvents(String regionId, LocalDateTime from, LocalDateTime to);

    Long countByRegionIdAndFechaEventoBetween(String regionId, LocalDateTime start, LocalDateTime end);

    // Standardized FIRMS-only count for the dashboard 7-day widget — without this
    // filter, heatAlerts7d silently counted every alert source, not just NASA_FIRMS.
    Long countByRegionIdAndFuenteAndFechaEventoBetween(String regionId, String fuente, LocalDateTime start, LocalDateTime end);

    Long countByNivelRiesgo(RiskLevel nivelRiesgo);

    // Comuna-scoped query against the persisted, geometrically-correct comunaId —
    // replaces on-read nearest-centroid assignment (ComunaRiskServiceImpl.assignFocosToComuna).
    List<HeatAlertEvent> findByComunaIdAndFechaEventoAfter(String comunaId, LocalDateTime after);

    // Region-scoped query against persisted comunaId set, used to derive region totals
    // as the sum of attributed comuna counts (TerritoryRiskServiceImpl) — replaces
    // on-read nearest-region-centroid reassignment (findNearestRegionId).
    List<HeatAlertEvent> findByComunaIdInAndFechaEventoAfter(List<String> comunaIds, LocalDateTime after);

    // Region-independent dedup key: a FIRMS detection's true identity is
    // (lat, lon, acq datetime, source), regardless of which region's bbox
    // fetched it. Two overlapping regions fetching the same physical pixel
    // collapse to one persisted row.
    boolean existsByLatitudAndLongitudAndFechaEventoAndFuente(
        Double latitud, Double longitud, LocalDateTime fechaEvento, String fuente);

    // Used by the startup backfill (BackfillComunaIdRunner) to find rows
    // attributed before comunaId existed. Stream avoids loading the full
    // result set into memory; callers must close the stream (try-with-resources).
    Stream<HeatAlertEvent> streamByFuenteAndComunaIdIsNull(String fuente);
}
