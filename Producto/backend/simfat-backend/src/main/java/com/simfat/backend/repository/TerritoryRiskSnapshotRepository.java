package com.simfat.backend.repository;

import com.simfat.backend.model.TerritoryRiskSnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TerritoryRiskSnapshotRepository extends MongoRepository<TerritoryRiskSnapshot, String> {

    Optional<TerritoryRiskSnapshot> findTopByRegionIdOrderByComputedAtDesc(String regionId);

    List<TerritoryRiskSnapshot> findByRegionIdOrderByComputedAtDesc(String regionId);
}
