package com.simfat.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.simfat.backend.model.ComunaInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.geo.GeoJsonMultiPolygon;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.test.context.TestPropertySource;

// Models OpenEoRepositoriesIntegrationTest: @DataMongoTest + auto-index-creation=true so
// the 2dsphere index actually exists and $geoIntersects queries run through the index,
// not a collection scan. Squares are hand-authored and small so expected results are
// obvious (Decision 5, Layer A).
@DataMongoTest
@TestPropertySource(properties = "spring.data.mongodb.auto-index-creation=true")
class ComunaGeoAttributionRepositoryIntegrationTest {

    @Autowired
    private ComunaInfoRepository comunaInfoRepository;

    @BeforeEach
    void cleanCollection() {
        comunaInfoRepository.deleteAll();
    }

    private GeoJsonMultiPolygon square(double west, double south, double east, double north) {
        List<Point> ring = List.of(
            new Point(west, south),
            new Point(east, south),
            new Point(east, north),
            new Point(west, north),
            new Point(west, south)
        );
        return new GeoJsonMultiPolygon(List.of(new GeoJsonPolygon(ring)));
    }

    private ComunaInfo comuna(String id, String regionId, GeoJsonMultiPolygon geometry) {
        ComunaInfo c = new ComunaInfo();
        c.setId(id);
        c.setRegionId(regionId);
        c.setNombre(id);
        c.setGeometry(geometry);
        return c;
    }

    @Test
    void findByGeometryIntersects_pointInsideComunaASquare_resolvesToComunaA() {
        comunaInfoRepository.save(comuna("comuna-A", "region-1", square(-73.0, -38.0, -72.0, -37.0)));
        comunaInfoRepository.save(comuna("comuna-B", "region-1", square(-71.0, -38.0, -70.0, -37.0)));

        List<ComunaInfo> matches = comunaInfoRepository.findByGeometryIntersects(-72.5, -37.5);

        assertEquals(1, matches.size());
        assertEquals("comuna-A", matches.get(0).getId());
    }

    @Test
    void findByGeometryIntersects_pointOutsideEverySquare_returnsEmpty() {
        comunaInfoRepository.save(comuna("comuna-A", "region-1", square(-73.0, -38.0, -72.0, -37.0)));

        List<ComunaInfo> matches = comunaInfoRepository.findByGeometryIntersects(0.0, 0.0);

        assertTrue(matches.isEmpty());
    }

    @Test
    void findByGeometryIntersects_boundaryPointOnSharedEdge_resolvesDeterministicallyAcrossRepeatedCalls() {
        comunaInfoRepository.save(comuna("comuna-A", "region-1", square(-73.0, -38.0, -72.0, -37.0)));
        comunaInfoRepository.save(comuna("comuna-B", "region-1", square(-72.0, -38.0, -71.0, -37.0)));

        // Shared edge at lon = -72.0
        List<ComunaInfo> first = comunaInfoRepository.findByGeometryIntersects(-72.0, -37.5);
        List<ComunaInfo> second = comunaInfoRepository.findByGeometryIntersects(-72.0, -37.5);

        assertFalse(first.isEmpty());
        assertEquals(first.get(0).getId(), second.get(0).getId());
    }

    @Test
    void findByGeometryIntersects_comunaWithNullGeometry_isSkippedWithoutBreakingValidQueries() {
        comunaInfoRepository.save(comuna("comuna-A", "region-1", square(-73.0, -38.0, -72.0, -37.0)));
        comunaInfoRepository.save(comuna("comuna-invalid", "region-1", null));

        List<ComunaInfo> matches = comunaInfoRepository.findByGeometryIntersects(-72.5, -37.5);

        assertEquals(1, matches.size());
        assertEquals("comuna-A", matches.get(0).getId());
    }

    // Note (post-review FIX 1): countByRegionIdAndGeometryNotNull was REMOVED — the
    // coverage probe is now PER-COMUNA (ComunaInfo#getGeometry() != null, a free
    // in-memory check owned by FirmsAttributionRouter), not a region-level DB count. The
    // region-level probe was the root cause of the comuna-granularity incident (see
    // design.md Decision 6 amendment). Coverage routing regression tests now live in
    // FirmsAttributionRouterTest.
}
