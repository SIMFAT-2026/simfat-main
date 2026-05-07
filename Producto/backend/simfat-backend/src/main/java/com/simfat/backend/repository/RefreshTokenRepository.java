package com.simfat.backend.repository;

import com.simfat.backend.model.RefreshTokenRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenRecord, String> {

    Optional<RefreshTokenRecord> findByTokenId(String tokenId);

    List<RefreshTokenRecord> findByUserIdAndRevokedAtIsNull(String userId);
}
