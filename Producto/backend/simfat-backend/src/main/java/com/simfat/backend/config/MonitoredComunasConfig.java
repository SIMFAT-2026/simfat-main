package com.simfat.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.Region;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.RegionRepository;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.geo.GeoJsonMultiPolygon;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Component;

/**
 * Siembra las comunas piloto desde GeoJSON GADM 4.1 al arranque.
 * Hace upsert por GID_3 (comunaId) sin tocar otros documentos.
 * Fuente: gadm.org — licencia libre para uso académico/no-comercial.
 */
@Component
public class MonitoredComunasConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(MonitoredComunasConfig.class);

    private static final Map<String, String> GEOJSON_BY_REGION = Map.of(
        "biobio", "static/geojson/comunas-biobio.geojson",
        "nuble", "static/geojson/comunas-nuble.geojson",
        "araucania", "static/geojson/comunas-araucania.geojson"
    );

    // [west, south, east, north] EPSG:4326 — bboxes piloto derivados de GADM 4.1
    private static final Map<String, Object[]> REGION_SEED = Map.of(
        "biobio",    new Object[]{"Región del Biobío",    "BIOBIO",    "SUR", 1500000.0, List.of(-73.6, -38.5, -71.0, -36.7)},
        "nuble",     new Object[]{"Región de Ñuble",       "NUBLE",     "SUR", 480000.0,  List.of(-73.0, -37.4, -71.0, -35.8)},
        "araucania", new Object[]{"Región de La Araucanía", "ARAUCANIA", "SUR", 1750000.0, List.of(-73.6, -40.0, -70.8, -37.6)}
    );

    private final ComunaInfoRepository comunaRepository;
    private final RegionRepository regionRepository;
    private final ObjectMapper objectMapper;

    public MonitoredComunasConfig(ComunaInfoRepository comunaRepository,
                                   RegionRepository regionRepository,
                                   ObjectMapper objectMapper) {
        this.comunaRepository = comunaRepository;
        this.regionRepository = regionRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureMonitoredComunas() {
        ensureRegions();
        for (Map.Entry<String, String> entry : GEOJSON_BY_REGION.entrySet()) {
            String regionId = entry.getKey();
            String path = entry.getValue();
            try {
                int upserted = seedFromGeoJson(regionId, path);
                LOGGER.info("monitored_comunas status=ok regionId={} upserted={}", regionId, upserted);
            } catch (Exception ex) {
                LOGGER.warn("monitored_comunas status=error regionId={} error={}", regionId, ex.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void ensureRegions() {
        for (Map.Entry<String, Object[]> entry : REGION_SEED.entrySet()) {
            String id = entry.getKey();
            Object[] data = entry.getValue();
            try {
                Region region = regionRepository.findById(id).orElseGet(() -> {
                    Region r = new Region();
                    r.setId(id);
                    return r;
                });
                region.setNombre((String) data[0]);
                region.setCodigo((String) data[1]);
                region.setZona((String) data[2]);
                region.setHectareasBosqueReferencia((Double) data[3]);
                if (region.getAoiBbox() == null) {
                    region.setAoiBbox((List<Double>) data[4]);
                }
                regionRepository.save(region);
                LOGGER.info("monitored_regions status=ok regionId={}", id);
            } catch (Exception ex) {
                LOGGER.warn("monitored_regions status=error regionId={} error={}", id, ex.getMessage());
            }
        }
    }

    private int seedFromGeoJson(String regionId, String resourcePath) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            LOGGER.warn("monitored_comunas status=file_not_found path={}", resourcePath);
            return 0;
        }

        try (InputStream is = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            JsonNode features = root.path("features");
            List<ComunaInfo> toSave = new ArrayList<>();

            for (JsonNode feature : features) {
                JsonNode props = feature.path("properties");
                String comunaId = props.path("comunaId").asText();
                if (comunaId.isBlank()) continue;

                ComunaInfo comuna = comunaRepository.findById(comunaId).orElseGet(() -> {
                    ComunaInfo c = new ComunaInfo();
                    c.setId(comunaId);
                    return c;
                });

                comuna.setNombre(props.path("nombre").asText());
                comuna.setProvincia(props.path("provincia").asText());
                comuna.setRegionId(regionId);
                comuna.setRegionGadm(props.path("regionGadm").asText());
                comuna.setGadmGid(comunaId);
                comuna.setCenterLat(props.path("centerLat").asDouble());
                comuna.setCenterLon(props.path("centerLon").asDouble());

                GeoJsonMultiPolygon geometry = null;
                try {
                    geometry = parseMultiPolygon(feature.path("geometry"));
                    validateRings(geometry);
                } catch (Exception ex) {
                    LOGGER.warn("monitored_comunas status=invalid_geometry comunaId={} error={}",
                        comunaId, ex.getMessage());
                    geometry = null;
                }
                comuna.setGeometry(geometry);

                toSave.add(comuna);
            }

            comunaRepository.saveAll(toSave);
            return toSave.size();
        }
    }

    /**
     * Converts a GeoJSON {@code geometry} node of type MultiPolygon into Spring
     * Data Mongo's {@link GeoJsonMultiPolygon}. Throws on missing/wrong type or
     * malformed coordinate structure so the caller's try/catch can log + skip
     * that single comuna without aborting startup (Decision 3).
     */
    private GeoJsonMultiPolygon parseMultiPolygon(JsonNode geometryNode) {
        if (geometryNode.isMissingNode() || geometryNode.isNull()) {
            throw new IllegalArgumentException("missing geometry node");
        }
        String type = geometryNode.path("type").asText();
        if (!"MultiPolygon".equals(type)) {
            throw new IllegalArgumentException("unexpected geometry type: " + type);
        }

        JsonNode polygonsNode = geometryNode.path("coordinates");
        if (!polygonsNode.isArray() || polygonsNode.isEmpty()) {
            throw new IllegalArgumentException("empty MultiPolygon coordinates");
        }

        List<GeoJsonPolygon> polygons = new ArrayList<>();
        for (JsonNode polygonNode : polygonsNode) {
            polygons.add(parsePolygon(polygonNode));
        }
        return new GeoJsonMultiPolygon(polygons);
    }

    private GeoJsonPolygon parsePolygon(JsonNode polygonNode) {
        if (!polygonNode.isArray() || polygonNode.isEmpty()) {
            throw new IllegalArgumentException("polygon has no rings");
        }
        // GeoJsonPolygon's constructor takes the outer ring as a varargs Point list;
        // GADM source polygons here are simple (no documented interior holes in the
        // pilot dataset), so only the first ring (the exterior) is mapped.
        JsonNode outerRing = polygonNode.get(0);
        List<Point> points = new ArrayList<>();
        for (JsonNode position : outerRing) {
            if (!position.isArray() || position.size() < 2) {
                throw new IllegalArgumentException("malformed coordinate position");
            }
            double lon = position.get(0).asDouble();
            double lat = position.get(1).asDouble();
            points.add(new Point(lon, lat));
        }
        return new GeoJsonPolygon(points);
    }

    /**
     * Structural sanity check, not a full topological self-intersection test
     * (that is left to MongoDB's 2dsphere indexer, which will reject/skip a
     * self-intersecting polygon per-document without crashing startup thanks
     * to the sparse-by-nullable geometry field). Checks ring closure and a
     * minimum vertex count for every ring of every polygon.
     */
    private void validateRings(GeoJsonMultiPolygon geometry) {
        for (GeoJsonPolygon polygon : geometry.getCoordinates()) {
            List<Point> ring = polygon.getCoordinates().get(0).getCoordinates();
            if (ring.size() < 4) {
                throw new IllegalArgumentException("ring has fewer than 4 positions");
            }
            Point first = ring.get(0);
            Point last = ring.get(ring.size() - 1);
            if (!isSamePoint(first, last)) {
                throw new IllegalArgumentException("ring is not closed (first != last)");
            }
        }
    }

    private boolean isSamePoint(Point a, Point b) {
        return Double.compare(a.getX(), b.getX()) == 0 && Double.compare(a.getY(), b.getY()) == 0;
    }
}
