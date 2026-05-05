package com.simfat.backend.repository;

import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.RiskLevel;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HeatAlertEventRepository extends MongoRepository<HeatAlertEvent, String> {

    List<HeatAlertEvent> findByRegionId(String regionId);

    Long countByRegionIdAndFechaEventoBetween(String regionId, LocalDateTime start, LocalDateTime end);

    Long countByNivelRiesgo(RiskLevel nivelRiesgo);
}
