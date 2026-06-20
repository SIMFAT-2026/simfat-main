package com.simfat.backend.repository;

import com.simfat.backend.model.CommunityChatModerationEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CommunityChatModerationEventRepository extends MongoRepository<CommunityChatModerationEvent, String> {
}
