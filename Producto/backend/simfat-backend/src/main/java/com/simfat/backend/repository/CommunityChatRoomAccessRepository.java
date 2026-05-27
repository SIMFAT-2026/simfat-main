package com.simfat.backend.repository;

import com.simfat.backend.model.CommunityChatRoomAccess;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityChatRoomAccessRepository extends JpaRepository<CommunityChatRoomAccess, String> {

    List<CommunityChatRoomAccess> findByUserIdAndRevokedAtIsNull(String userId);

    @Modifying
    @Query("update CommunityChatRoomAccess access set access.revokedAt = CURRENT_TIMESTAMP where access.userId = :userId and access.revokedAt is null")
    void revokeActiveByUserId(@Param("userId") String userId);
}
