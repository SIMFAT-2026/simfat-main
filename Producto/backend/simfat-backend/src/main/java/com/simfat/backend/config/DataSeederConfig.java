package com.simfat.backend.config;

import com.simfat.backend.model.AlertRule;
import com.simfat.backend.model.ForestLossRecord;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.Region;
import com.simfat.backend.model.RiskLevel;
import com.simfat.backend.repository.AlertRuleRepository;
import com.simfat.backend.repository.ForestLossRecordRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import com.simfat.backend.repository.RegionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeederConfig {

    @Bean
    @ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
    CommandLineRunner seedData(
        RegionRepository regionRepository,
        ForestLossRecordRepository forestLossRepository,
        HeatAlertEventRepository heatAlertRepository,
        AlertRuleRepository alertRuleRepository,
        @Value("${app.seed.exam.enabled:false}") boolean examImportEnabled
    ) {
        return args -> {
            if (examImportEnabled) {
                return;
            }

            if (regionRepository.count() > 0 || forestLossRepository.count() > 0 || heatAlertRepository.count() > 0) {
                return;
            }

            Region regionCentro = new Region();
            regionCentro.setNombre("Region Centro Andina");
            regionCentro.setCodigo("SIM-RA-01");
            regionCentro.setZona("CENTRO");
            regionCentro.setHectareasBosqueReferencia(185000.0);

            Region regionSur = new Region();
            regionSur.setNombre("Region Sur Bosque Humedo");
            regionSur.setCodigo("SIM-RS-02");
            regionSur.setZona("SUR");
            regionSur.setHectareasBosqueReferencia(240000.0);

            Region regionNorte = new Region();
            regionNorte.setNombre("Region Norte Secano");
            regionNorte.setCodigo("SIM-RN-03");
            regionNorte.setZona("NORTE");
            regionNorte.setHectareasBosqueReferencia(120000.0);

            List<Region> savedRegions = regionRepository.saveAll(List.of(regionCentro, regionSur, regionNorte));

            Region centro = savedRegions.get(0);
            Region sur = savedRegions.get(1);
            Region norte = savedRegions.get(2);

            forestLossRepository.saveAll(List.of(
                buildLoss(centro.getId(), 2022, 530.0, 0.29, "SIMFAT-Observatorio"),
                buildLoss(centro.getId(), 2023, 710.0, 0.38, "SIMFAT-Observatorio"),
                buildLoss(sur.getId(), 2022, 840.0, 0.35, "Global Forest Watch"),
                buildLoss(sur.getId(), 2023, 1260.0, 0.53, "Global Forest Watch"),
                buildLoss(norte.getId(), 2023, 980.0, 0.82, "SIMFAT-Teledeteccion")
            ));

            heatAlertRepository.saveAll(List.of(
                buildHeatAlert(centro.getId(), -35.44, -71.66, RiskLevel.MEDIO, "NASA FIRMS", "Foco de calor cercano a area de interfaz"),
                buildHeatAlert(sur.getId(), -39.81, -73.24, RiskLevel.ALTO, "NASA FIRMS", "Multiples focos en ventana de 24h"),
                buildHeatAlert(norte.getId(), -27.37, -70.33, RiskLevel.CRITICO, "NASA FIRMS", "Evento persistente con viento fuerte")
            ));

            if (alertRuleRepository.count() == 0) {
                AlertRule globalRule = new AlertRule();
                globalRule.setNombre("Regla Global SIMFAT");
                globalRule.setRegionId(null);
                globalRule.setUmbralPorcentajePerdida(0.5);
                globalRule.setUmbralEventosCalor(3);
                globalRule.setActiva(true);
                alertRuleRepository.save(globalRule);
            }
        };
    }

    private ForestLossRecord buildLoss(String regionId, int year, double hectares, double percentage, String source) {
        ForestLossRecord record = new ForestLossRecord();
        record.setRegionId(regionId);
        record.setAnio(year);
        record.setHectareasPerdidas(hectares);
        record.setPorcentajePerdida(percentage);
        record.setFuente(source);
        record.setFechaRegistro(LocalDateTime.now().minusDays(5));
        return record;
    }

    private HeatAlertEvent buildHeatAlert(
        String regionId,
        double latitude,
        double longitude,
        RiskLevel riskLevel,
        String source,
        String description
    ) {
        HeatAlertEvent event = new HeatAlertEvent();
        event.setRegionId(regionId);
        event.setFechaEvento(LocalDateTime.now().minusHours(8));
        event.setNivelRiesgo(riskLevel);
        event.setLatitud(latitude);
        event.setLongitud(longitude);
        event.setFuente(source);
        event.setDescripcion(description);
        return event;
    }
}
