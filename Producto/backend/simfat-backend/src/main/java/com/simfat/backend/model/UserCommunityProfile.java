package com.simfat.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_community_profiles")
public class UserCommunityProfile {

    @Id
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "primary_region_id", length = 80)
    private String primaryRegionId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPrimaryRegionId() {
        return primaryRegionId;
    }

    public void setPrimaryRegionId(String primaryRegionId) {
        this.primaryRegionId = primaryRegionId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
