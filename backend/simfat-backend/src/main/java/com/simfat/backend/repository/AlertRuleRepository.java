package com.simfat.backend.repository;

import com.simfat.backend.model.AlertRule;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AlertRuleRepository extends MongoRepository<AlertRule, String> {

    List<AlertRule> findByActivaTrue();

    List<AlertRule> findByRegionIdAndActivaTrue(String regionId);
}

