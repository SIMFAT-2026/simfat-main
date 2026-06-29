package com.simfat.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.simfat.backend.model.ComunaInfo;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.geo.GeoJsonMultiPolygon;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.test.context.TestPropertySource;

// Models OpenEoRepositoriesIntegrationTest's pattern: @DataMongoTest +
// auto-index-creation=true against the local/CI MongoDB configured in
// src/test/resources/application.properties (no embedded Mongo in this
// project — see design.md Decision 5). Exercises real $geoIntersects against
// seeded comuna polygons, per spec scenarios under "Sync-time point-in-polygon
// attribution".
@DataMongoTest
@TestPropertySource(properties = "spring.data.mongodb.auto-index-creation=true")
class ComunaGeoAttributionRepositoryIntegrationTest {

    @Autowired
    private ComunaInfoRepository comunaInfoRepository;

    @BeforeEach
    void cleanCollection() {
        comunaInfoRepository.deleteAll();
    }

    @Test
    void findOneByGeometryIntersects_pointInsideComunaA_resolvesToComunaA() {
        seedSquareComuna("comuna-a", 0.0, 0.0, 1.0, 1.0);
        seedSquareComuna("comuna-b", 2.0, 0.0, 3.0, 1.0);

        Optional<ComunaInfo> result = comunaInfoRepository.findOneByGeometryIntersects(
            new GeoJsonPoint(0.5, 0.5));

        assertTrue(result.isPresent());
        assertEquals("comuna-a", result.get().getId());
    }

    @Test
    void findByGeometryIntersects_pointOutsideEverySquare_isEmpty() {
        seedSquareComuna("comuna-a", 0.0, 0.0, 1.0, 1.0);
        seedSquareComuna("comuna-b", 2.0, 0.0, 3.0, 1.0);

        List<ComunaInfo> matches = comunaInfoRepository.findByGeometryIntersects(50.0, 50.0);

        assertTrue(matches.isEmpty());
        assertFalse(comunaInfoRepository.findOneByGeometryIntersects(new GeoJsonPoint(50.0, 50.0)).isPresent());
    }

    @Test
    void findOneByGeometryIntersects_boundaryPoint_resolvesDeterministicallyAcrossRepeatedRuns() {
        // comuna-a: [0,0]-[1,1]; comuna-b: [1,0]-[2,1] — share the edge x=1.
        seedSquareComuna("comuna-a", 0.0, 0.0, 1.0, 1.0);
        seedSquareComuna("comuna-b", 1.0, 0.0, 2.0, 1.0);

        GeoJsonPoint boundaryPoint = new GeoJsonPoint(1.0, 0.5);

        Optional<ComunaInfo> first = comunaInfoRepository.findOneByGeometryIntersects(boundaryPoint);
        Optional<ComunaInfo> second = comunaInfoRepository.findOneByGeometryIntersects(boundaryPoint);

        assertTrue(first.isPresent());
        assertEquals(first.get().getId(), second.get().getId());
    }

    @Test
    void malformedGeometryComuna_isSkipped_validComunasStillQueryable() {
        // comuna-valid has real geometry; comuna-no-geometry has none (as if seed
        // validation skipped it per Decision 3) — sparse index excludes it, and
        // it must not break lookups against the valid comuna.
        seedSquareComuna("comuna-valid", 0.0, 0.0, 1.0, 1.0);

        ComunaInfo noGeometry = new ComunaInfo();
        noGeometry.setId("comuna-no-geometry");
        noGeometry.setNombre("Sin Geometria");
        noGeometry.setRegionId("biobio");
        noGeometry.setGeometry(null);
        comunaInfoRepository.save(noGeometry);

        Optional<ComunaInfo> result = comunaInfoRepository.findOneByGeometryIntersects(
            new GeoJsonPoint(0.5, 0.5));

        assertTrue(result.isPresent());
        assertEquals("comuna-valid", result.get().getId());
        assertEquals(2, comunaInfoRepository.count());
    }

    private void seedSquareComuna(String id, double west, double south, double east, double north) {
        ComunaInfo comuna = new ComunaInfo();
        comuna.setId(id);
        comuna.setNombre(id);
        comuna.setRegionId("biobio");
        comuna.setGeometry(squarePolygon(west, south, east, north));
        comunaInfoRepository.save(comuna);
    }

    private GeoJsonMultiPolygon squarePolygon(double west, double south, double east, double north) {
        GeoJsonPolygon square = new GeoJsonPolygon(
            new org.springframework.data.geo.Point(west, south),
            new org.springframework.data.geo.Point(east, south),
            new org.springframework.data.geo.Point(east, north),
            new org.springframework.data.geo.Point(west, north),
            new org.springframework.data.geo.Point(west, south));
        return new GeoJsonMultiPolygon(List.of(square));
    }
}
