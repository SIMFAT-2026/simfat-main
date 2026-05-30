package com.simfat.backend.repository;

import com.simfat.backend.model.CommunityChatRoom;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CommunityChatRoomRepository extends MongoRepository<CommunityChatRoom, String> {

    List<CommunityChatRoom> findByActiveTrueOrderByTypeAscNameAsc();
}
