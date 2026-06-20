package com.simfat.backend.repository;

import com.simfat.backend.model.VerificationEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VerificationEventRepository extends JpaRepository<VerificationEvent, String> {

    List<VerificationEvent> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Usuarios cuyo ultimo VerificationEvent es de tipo IDENTITY_RESET
     * (no tienen ningun evento posterior de revision admin).
     */
    @Query(value = """
        SELECT ve.* FROM verification_events ve
        WHERE ve.event_type = 'IDENTITY_RESET'
          AND NOT EXISTS (
              SELECT 1 FROM verification_events ve2
              WHERE ve2.user_id = ve.user_id
                AND ve2.created_at > ve.created_at
          )
        ORDER BY ve.created_at DESC
        """, nativeQuery = true)
    List<VerificationEvent> findPendingIdentityResets();
}
