package com.simfat.backend.repository;

import com.simfat.backend.model.ForestLossRecord;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ForestLossRecordRepository extends MongoRepository<ForestLossRecord, String> {

    List<ForestLossRecord> findByRegionId(String regionId);

    List<ForestLossRecord> findByAnio(Integer anio);

    List<ForestLossRecord> findByRegionIdAndAnio(String regionId, Integer anio);

    List<ForestLossRecord> findAllByOrderByAnioAsc();
}
