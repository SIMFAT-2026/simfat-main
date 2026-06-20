package com.simfat.backend.service;

import com.simfat.backend.dto.account.AccountProfileDTO;
import com.simfat.backend.dto.account.ChangePasswordRequestDTO;
import com.simfat.backend.dto.account.UpdateProfileRequestDTO;

public interface AccountService {

    AccountProfileDTO getProfile(String userId);

    AccountProfileDTO updateProfile(String userId, UpdateProfileRequestDTO request);

    void changePassword(String userId, ChangePasswordRequestDTO request);
}
