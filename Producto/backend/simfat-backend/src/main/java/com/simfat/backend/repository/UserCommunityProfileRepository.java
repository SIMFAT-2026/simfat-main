package com.simfat.backend.repository;

import com.simfat.backend.model.UserCommunityProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCommunityProfileRepository extends JpaRepository<UserCommunityProfile, String> {
}
