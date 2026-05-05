package com.simfat.backend.repository;

import com.simfat.backend.model.CitizenReport;
import com.simfat.backend.model.CitizenReportStatus;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CitizenReportRepository extends MongoRepository<CitizenReport, String> {

    List<CitizenReport> findByRegionId(String regionId);

    List<CitizenReport> findByStatus(CitizenReportStatus status);

    List<CitizenReport> findByCategoryIgnoreCase(String category);

    List<CitizenReport> findByRegionIdAndStatus(String regionId, CitizenReportStatus status);
}
