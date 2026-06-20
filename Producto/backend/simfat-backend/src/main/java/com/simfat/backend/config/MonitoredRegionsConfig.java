package com.simfat.backend.config;

import com.simfat.backend.model.Region;
import com.simfat.backend.repository.RegionRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Garantiza que las dos regiones piloto existan en MongoDB con IDs explicitos
 * y bboxes configurados. Se ejecuta al arranque y hace upsert sin tocar otros
 * documentos existentes en la coleccion.
 */
@Component
public class MonitoredRegionsConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(MonitoredRegionsConfig.class);

    private final RegionRepository regionRepository;

    public MonitoredRegionsConfig(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureMonitoredRegions() {
        upsertRegion(
            "biobio",
            "Biobio",
            "BIOBIO",
            "SUR",
            185000.0,
            List.of(-74.1, -38.9, -71.0, -36.3)
        );
        upsertRegion(
            "nuble",
            "Ñuble",
            "NUBLE",
            "SUR",
            480000.0,
            List.of(-73.0, -37.4, -71.0, -35.8)
        );
        upsertRegion(
            "araucania",
            "La Araucania",
            "ARAUCANIA",
            "SUR",
            240000.0,
            List.of(-73.9, -39.8, -71.2, -37.8)
        );
    }

    private void upsertRegion(String id, String nombre, String codigo, String zona,
                               double hectareas, List<Double> bbox) {
        Region region = regionRepository.findById(id).orElseGet(() -> {
            Region r = new Region();
            r.setId(id);
            return r;
        });

        region.setNombre(nombre);
        region.setCodigo(codigo);
        region.setZona(zona);
        region.setHectareasBosqueReferencia(hectareas);
        region.setAoiBbox(bbox);

        regionRepository.save(region);
        LOGGER.info("monitored_regions status=upserted regionId={} codigo={}", id, codigo);
    }
}
