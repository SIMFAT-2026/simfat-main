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

    // Comuna-scoped FIRMS reads (Decision 6, covered-comuna path): comunaId is the
    // canonical geo-attribution source. Null comunaId rows (offshore, or coverage-gap
    // rows not yet backfilled) can never match these queries by construction.
    //
    // FIX 6 (post-review, finding C7): these methods are public Spring Data repository
    // methods (cannot be access-restricted at the interface level), but the SANCTIONED
    // way to read FIRMS-by-attribution is exclusively through FirmsAttributionRouter,
    // which owns the covered-vs-uncovered routing decision. A caller invoking these
    // directly would misinterpret an empty result from an uncovered comuna/region as "no
    // fires" instead of "should have used the centroid fallback" — always go through the
    // router.
    List<HeatAlertEvent> findByComunaIdAndFechaEventoAfter(String comunaId, LocalDateTime after);

    List<HeatAlertEvent> findByComunaIdInAndFechaEventoAfter(List<String> comunaIds, LocalDateTime after);

    // Backfill source stream (revised Decision 1): rows persisted before comunaId
    // attribution existed, or inserted while a region was still a coverage gap.
    Stream<HeatAlertEvent> streamByFuenteAndComunaIdIsNull(String fuente);

    // FIX 2 (post-review, finding C6): candidate pool for the centroid fallback,
    // intentionally NOT scoped by persisted regionId. Decision 2 made FIRMS dedup
    // region-independent — a physical detection persists exactly once, owned by
    // whichever region's cron leg synced it first, with regionId set to that leg's
    // region. Pre-filtering the fallback's candidate pool by regionId (as the original
    // pre-incident centroid logic implicitly relied on, back when dedup WAS region-scoped
    // and findByRegionId(thisRegion) was guaranteed complete) means an overlapping
    // neighbor region's fallback can never even see a row persisted under a different
    // region's leg, silently undercounting. Centroid distance — not persisted regionId —
    // must decide true ownership for uncovered regions/comunas.
    List<HeatAlertEvent> findByFuenteAndFechaEventoAfter(String fuente, LocalDateTime after);

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

    Long countByRegionIdAndFuenteAndFechaEventoBetween(String regionId, String fuente, LocalDateTime start, LocalDateTime end);

    Long countByNivelRiesgo(RiskLevel nivelRiesgo);

    // Region-independent identity dedup (Decision 2): a FIRMS detection's true identity
    // is (lat, lon, acq datetime, source) — independent of which region's bbox fetched
    // it. Replaces the regionId-scoped check so the same physical pixel fetched by two
    // overlapping region bboxes collapses to one row instead of being double-inserted.
    boolean existsByLatitudAndLongitudAndFechaEventoAndFuente(
        Double latitud, Double longitud, LocalDateTime fechaEvento, String fuente);
}
