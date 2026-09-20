package com.simfat.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.simfat.backend.model.ComunaInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.geo.GeoJsonMultiPolygon;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.data.geo.Point;

// Requires a real MongoDB (same setup as the other @DataMongoTest classes); not runnable without it.
@DataMongoTest
class ComunaInfoProjectionIntegrationTest {

    @Autowired
    private ComunaInfoRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void namesProjectionReturnsIdAndNombreWithoutGeometry() {
        ComunaInfo c = new ComunaInfo();
        c.setId("c1");
        c.setNombre("Tome");
        c.setRegionId("biobio");
        c.setProvincia("Concepcion");
        c.setGeometry(new GeoJsonMultiPolygon(List.of(new GeoJsonPolygon(List.of(
            new Point(-73.0, -36.0), new Point(-72.9, -36.0), new Point(-72.9, -36.1), new Point(-73.0, -36.0))))));
        repository.save(c);

        List<ComunaInfo> names = repository.findNamesByRegionId("biobio");

        assertThat(names).hasSize(1);
        assertThat(names.get(0).getId()).isEqualTo("c1");
        assertThat(names.get(0).getNombre()).isEqualTo("Tome");
        assertThat(names.get(0).getGeometry()).isNull();
        assertThat(names.get(0).getProvincia()).isNull();
    }
}
