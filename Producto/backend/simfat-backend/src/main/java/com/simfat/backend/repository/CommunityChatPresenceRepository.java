package com.simfat.backend.repository;

import com.simfat.backend.model.CommunityChatPresence;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CommunityChatPresenceRepository extends MongoRepository<CommunityChatPresence, String> {

    Optional<CommunityChatPresence> findByUserIdAndRoomId(String userId, String roomId);
}
