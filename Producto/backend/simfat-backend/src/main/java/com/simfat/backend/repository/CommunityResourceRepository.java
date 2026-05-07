package com.simfat.backend.repository;

import com.simfat.backend.model.CommunityResource;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CommunityResourceRepository extends MongoRepository<CommunityResource, String> {

    List<CommunityResource> findByRegionIdOrderByCreatedAtDesc(String regionId);

    List<CommunityResource> findAllByOrderByCreatedAtDesc();
}
