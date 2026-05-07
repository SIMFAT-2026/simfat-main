package com.simfat.backend.repository;

import com.simfat.backend.model.CommunityContact;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CommunityContactRepository extends MongoRepository<CommunityContact, String> {

    List<CommunityContact> findByRegionIdOrderByCreatedAtDesc(String regionId);

    List<CommunityContact> findAllByOrderByCreatedAtDesc();
}
