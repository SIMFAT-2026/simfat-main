package com.simfat.backend.repository;

import com.simfat.backend.model.CommunityBoardPost;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CommunityBoardPostRepository extends MongoRepository<CommunityBoardPost, String> {

    List<CommunityBoardPost> findByRegionIdOrderByPublishedAtDesc(String regionId);

    List<CommunityBoardPost> findAllByOrderByPublishedAtDesc();
}
