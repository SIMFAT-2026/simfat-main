package com.simfat.backend.repository;

import com.simfat.backend.model.ComunaMapbiomasStats;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data Mongo repository for {@code comuna_mapbiomas_stats} (S1b2). Kept minimal:
 * query methods below are exactly what {@code MapbiomasSeedLoader} (idempotent upsert by
 * comunaId+dataVersion) and the future S2a {@code MapbiomasSusceptibilityService} (bulk
 * per-run/version read, single-comuna lookup, design NFR-7) plausibly need. Do not add
 * broader query surface until a concrete slice needs it.
 */
public interface ComunaMapbiomasStatsRepository extends MongoRepository<ComunaMapbiomasStats, String> {

    List<ComunaMapbiomasStats> findByComunaId(String comunaId);

    Optional<ComunaMapbiomasStats> findByComunaIdAndDataVersion(String comunaId, String dataVersion);

    List<ComunaMapbiomasStats> findByRegionId(String regionId);

    // Bulk load for one scoring run/version (NFR-7: "bulk stats read with per-run/version
    // cache" — S2a reads all comunas of the pinned dataVersion once instead of N single
    // lookups).
    List<ComunaMapbiomasStats> findByDataVersion(String dataVersion);
}
