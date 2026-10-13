package com.simfat.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * MonitoredComunasConfig upserts ComunaInfo.nombre from the GeoJSON "nombre" property on
 * every boot, so the GeoJSON is the source of truth for every server-generated comuna
 * label (heat alerts, notifications, risk snapshots, alerts layer). GADM NAME_3 drops
 * spaces ("LosAngeles"); these names must match the curated frontend map exactly.
 */
class ComunaGeoJsonCuratedNamesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String[] FILES = {
        "static/geojson/comunas-biobio.geojson",
        "static/geojson/comunas-nuble.geojson",
        "static/geojson/comunas-araucania.geojson"
    };

    private Map<String, String> actualNamesById() throws Exception {
        Map<String, String> actual = new HashMap<>();
        for (String path : FILES) {
            try (InputStream is = new ClassPathResource(path).getInputStream()) {
                for (JsonNode f : MAPPER.readTree(is).path("features")) {
                    actual.put(f.path("properties").path("comunaId").asText(),
                        f.path("properties").path("nombre").asText());
                }
            }
        }
        return actual;
    }

    @Test
    void allGeoJsonFeaturesUseCuratedDisplayNames() throws Exception {
        Map<String, String> expected;
        try (InputStream is = new ClassPathResource("curated-comuna-names.json").getInputStream()) {
            expected = MAPPER.readValue(is, new TypeReference<Map<String, String>>() {});
        }
        Map<String, String> actual = actualNamesById();

        assertThat(expected).hasSize(86);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void multiWordComunasKeepTheirSpaces() throws Exception {
        Map<String, String> actual = actualNamesById();
        assertThat(actual.get("CHL.6.2.5_1")).isEqualTo("Los Ángeles");
        assertThat(actual.get("CHL.6.3.9_1")).isEqualTo("San Pedro de la Paz");
        assertThat(actual.get("CHL.13.1.3_1")).isEqualTo("Chillán Viejo");
        assertThat(actual.get("CHL.3.1.12_1")).isEqualTo("Padre Las Casas");
    }
}
