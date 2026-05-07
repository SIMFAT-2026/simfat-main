package com.simfat.backend.repository;

import com.simfat.backend.model.PasswordResetToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findByUserIdAndConsumedAtIsNull(String userId);
}
