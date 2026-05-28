package com.simfat.backend.repository;

import com.simfat.backend.model.CommunityChatMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CommunityChatMessageRepository extends MongoRepository<CommunityChatMessage, String> {

    List<CommunityChatMessage> findByRoomIdOrderByCreatedAtAsc(String roomId, Pageable pageable);

    List<CommunityChatMessage> findByRoomIdAndCreatedAtAfterOrderByCreatedAtAsc(String roomId, LocalDateTime after, Pageable pageable);

    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
