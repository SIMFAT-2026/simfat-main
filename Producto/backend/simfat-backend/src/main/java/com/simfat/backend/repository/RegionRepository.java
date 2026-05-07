package com.simfat.backend.repository;

import com.simfat.backend.model.Region;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RegionRepository extends MongoRepository<Region, String> {

    Optional<Region> findByCodigo(String codigo);
}

