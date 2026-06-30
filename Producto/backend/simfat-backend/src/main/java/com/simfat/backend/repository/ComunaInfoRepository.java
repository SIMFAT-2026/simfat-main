package com.simfat.backend.repository;

import com.simfat.backend.model.ComunaInfo;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ComunaInfoRepository extends MongoRepository<ComunaInfo, String> {

    List<ComunaInfo> findByRegionId(String regionId);

    long countByRegionId(String regionId);
}
