package com.simfat.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.repository.ComunaInfoRepository;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
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
        "biobio", "geojson/comunas-biobio.geojson",
        "araucania", "geojson/comunas-araucania.geojson"
    );

    private final ComunaInfoRepository comunaRepository;
    private final ObjectMapper objectMapper;

    public MonitoredComunasConfig(ComunaInfoRepository comunaRepository, ObjectMapper objectMapper) {
        this.comunaRepository = comunaRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureMonitoredComunas() {
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

                toSave.add(comuna);
            }

            comunaRepository.saveAll(toSave);
            return toSave.size();
        }
    }
}
