package com.simfat.backend.service;

import com.simfat.backend.dto.auth.AuthTokensResponseDTO;
import com.simfat.backend.dto.auth.AuthUserDTO;
import com.simfat.backend.dto.auth.ForgotPasswordRequestDTO;
import com.simfat.backend.dto.auth.LoginRequestDTO;
import com.simfat.backend.dto.auth.LogoutRequestDTO;
import com.simfat.backend.dto.auth.RefreshTokenRequestDTO;
import com.simfat.backend.dto.auth.RegisterRequestDTO;
import com.simfat.backend.dto.auth.ResetPasswordRequestDTO;
import com.simfat.backend.dto.auth.SeedUsersResponseDTO;

public interface AuthService {

    AuthTokensResponseDTO register(RegisterRequestDTO request, String remoteIp, String userAgent);

    AuthTokensResponseDTO login(LoginRequestDTO request, String remoteIp, String userAgent);

    AuthTokensResponseDTO refresh(RefreshTokenRequestDTO request, String remoteIp, String userAgent);

    AuthUserDTO getCurrentUser(String userId);

    void logout(String userId, LogoutRequestDTO request);

    void forgotPassword(ForgotPasswordRequestDTO request, String remoteIp);

    void resetPassword(ResetPasswordRequestDTO request);

    SeedUsersResponseDTO seedUsers(int count);
}

