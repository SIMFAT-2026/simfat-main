package com.simfat.backend.repository;

import com.simfat.backend.model.IndicatorType;
import com.simfat.backend.model.OpenEoJobRun;
import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OpenEoJobRunRepository extends MongoRepository<OpenEoJobRun, String> {

    boolean existsByRegionIdAndIndicatorAndPeriodStartAndPeriodEndAndStatusIn(
        String regionId,
        IndicatorType indicator,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        Collection<String> statuses
    );
}
