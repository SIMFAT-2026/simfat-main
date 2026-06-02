package com.simfat.backend.repository;

import com.simfat.backend.model.ComunaRiskSnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ComunaRiskSnapshotRepository extends MongoRepository<ComunaRiskSnapshot, String> {

    Optional<ComunaRiskSnapshot> findTopByComunaIdOrderByComputedAtDesc(String comunaId);

    List<ComunaRiskSnapshot> findTopByRegionIdOrderByComputedAtDesc(String regionId);

    List<ComunaRiskSnapshot> findByRegionIdOrderByComputedAtDesc(String regionId);

    List<ComunaRiskSnapshot> findByComunaIdAndComputedAtAfterOrderByComputedAtDesc(String comunaId, java.time.LocalDateTime after);
}
