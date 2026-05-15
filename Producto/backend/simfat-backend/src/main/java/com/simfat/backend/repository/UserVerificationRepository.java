package com.simfat.backend.repository;

import com.simfat.backend.model.UserVerification;
import com.simfat.backend.model.VerificationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserVerificationRepository extends JpaRepository<UserVerification, String> {

    List<UserVerification> findByStatus(VerificationStatus status);
}
