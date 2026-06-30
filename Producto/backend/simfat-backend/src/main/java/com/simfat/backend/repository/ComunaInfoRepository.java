package com.simfat.backend.repository;

import com.simfat.backend.model.ComunaInfo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ComunaInfoRepository extends MongoRepository<ComunaInfo, String> {

    List<ComunaInfo> findByRegionId(String regionId);

    long countByRegionId(String regionId);

    /**
     * Point-in-polygon lookup: returns comunas whose 2dsphere {@code geometry}
     * intersects the given point ($geoIntersects). GADM polygons are
     * non-overlapping by construction, so at most one match is expected in
     * practice; on the rare boundary point that intersects more than one
     * polygon, the canonical attribution source is
     * {@link #findOneByGeometryIntersects(Point)}, which deterministically
     * takes the first match.
     *
     * Implemented as an explicit {@code $geoIntersects} query (not a derived
     * query method) because this Spring Data version has no {@code Part.Type}
     * keyword for geo-intersection — only NEAR/WITHIN are supported as
     * derived-query keywords.
     */
    @Query("{ 'geometry': { '$geoIntersects': { '$geometry': { 'type': 'Point', 'coordinates': [ ?0, ?1 ] } } } }")
    List<ComunaInfo> findByGeometryIntersects(double longitude, double latitude);

    /**
     * Deterministic single-match wrapper around {@link #findByGeometryIntersects(double, double)}.
     * This is the one expression reused by sync-time attribution
     * (NasaFirmsServiceImpl) and the startup backfill (BackfillComunaIdRunner) —
     * comuna attribution logic must exist in exactly this one place.
     */
    default Optional<ComunaInfo> findOneByGeometryIntersects(Point point) {
        List<ComunaInfo> matches = findByGeometryIntersects(point.getX(), point.getY());
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }
}
