package com.simfat.backend.repository;

import com.simfat.backend.model.DashboardRegionSnapshot;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DashboardRegionSnapshotRepository extends MongoRepository<DashboardRegionSnapshot, String> {

    Optional<DashboardRegionSnapshot> findByRegionId(String regionId);
}
