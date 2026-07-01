package com.simfat.backend.repository;

import com.simfat.backend.model.ComunaInfo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ComunaInfoRepository extends MongoRepository<ComunaInfo, String> {

    List<ComunaInfo> findByRegionId(String regionId);

    long countByRegionId(String regionId);

    // Point-in-polygon attribution (Decision 4). Implemented as an explicit @Query
    // instead of a derived `findByGeometryIntersects(Point)` method: this Spring Data
    // Commons version has no `GeometryIntersects` Part.Type keyword (only NEAR/WITHIN
    // are recognized geo predicates), so the derived form fails at context startup with
    // PropertyReferenceException. The raw $geoIntersects query is functionally identical
    // and uses the same 2dsphere index.
    @Query("{ 'geometry': { '$geoIntersects': { '$geometry': { 'type': 'Point', 'coordinates': [ ?0, ?1 ] } } } }")
    List<ComunaInfo> findByGeometryIntersects(double longitude, double latitude);

    // Single-expression attribution call, reused by sync (NasaFirmsServiceImpl) and
    // backfill (BackfillComunaIdRunner) — satisfies the single-source-of-truth invariant
    // (Architectural Invariant 1). GADM polygons are non-overlapping by construction, so
    // at most one match is expected; .get(0) is the deterministic first-match tie-break
    // on the rare floating-point boundary case.
    default Optional<ComunaInfo> findOneByGeometryIntersects(GeoJsonPoint point) {
        List<ComunaInfo> matches = findByGeometryIntersects(point.getX(), point.getY());
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }
}
