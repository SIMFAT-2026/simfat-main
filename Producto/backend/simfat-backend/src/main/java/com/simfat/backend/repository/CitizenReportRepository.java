package com.simfat.backend.repository;

import com.simfat.backend.model.CitizenReport;
import com.simfat.backend.model.CitizenReportStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface CitizenReportRepository extends MongoRepository<CitizenReport, String> {

    List<CitizenReport> findByRegionId(String regionId);

    List<CitizenReport> findByRegionIdAndCreatedAtBetween(String regionId, LocalDateTime from, LocalDateTime to);

    // Anonymous reads: status filter and half-open window [from, endExclusive) are applied by the
    // database (Spring Data "Between" is exclusive on both ends), newest first; the page size is the
    // hard cap. Non-validated reports are never loaded.
    @Query(value = "{ 'status': ?0, 'createdAt': { '$gte': ?1, '$lt': ?2 } }", sort = "{ 'createdAt': -1 }")
    List<CitizenReport> findByStatusInWindowNewestFirst(
        CitizenReportStatus status, LocalDateTime from, LocalDateTime endExclusive, Pageable pageable);

    @Query(value = "{ 'regionId': ?0, 'status': ?1, 'createdAt': { '$gte': ?2, '$lt': ?3 } }",
        sort = "{ 'createdAt': -1 }")
    List<CitizenReport> findByRegionIdAndStatusInWindowNewestFirst(
        String regionId, CitizenReportStatus status, LocalDateTime from, LocalDateTime endExclusive, Pageable pageable);

    List<CitizenReport> findByStatus(CitizenReportStatus status);

    List<CitizenReport> findByCategoryIgnoreCase(String category);

    List<CitizenReport> findByRegionIdAndStatus(String regionId, CitizenReportStatus status);

    List<CitizenReport> findByStatusAndValidatedAtBefore(CitizenReportStatus status, LocalDateTime cutoff);

    List<CitizenReport> findByStatusAndStaleCountAndStaleSinceBefore(
        CitizenReportStatus status,
        int staleCount,
        LocalDateTime cutoff
    );
}
