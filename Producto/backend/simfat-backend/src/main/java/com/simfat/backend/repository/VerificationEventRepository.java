package com.simfat.backend.repository;

import com.simfat.backend.model.VerificationEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationEventRepository extends JpaRepository<VerificationEvent, String> {

    List<VerificationEvent> findByUserIdOrderByCreatedAtDesc(String userId);
}
