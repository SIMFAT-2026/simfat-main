package com.simfat.backend.repository;

import com.simfat.backend.model.ComunaFwiState;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data Mongo repository for {@code comuna_fwi_state} (S1d1). One document per comuna,
 * keyed by comunaId ({@code _id}), so the inherited {@code findById}/{@code save} from
 * {@link MongoRepository} are all {@code ComunaFwiStateService} needs — no custom query methods
 * yet. Do not add broader query surface until a concrete later slice needs it (same policy as
 * {@code ComunaMapbiomasStatsRepository}).
 */
public interface ComunaFwiStateRepository extends MongoRepository<ComunaFwiState, String> {
}
