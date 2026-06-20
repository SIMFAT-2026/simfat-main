package com.simfat.backend.repository;

import com.simfat.backend.model.CommunityChatRoomAccess;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityChatRoomAccessRepository extends JpaRepository<CommunityChatRoomAccess, String> {

    List<CommunityChatRoomAccess> findByUserIdAndRevokedAtIsNull(String userId);
}
