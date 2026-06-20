package com.simfat.backend.controller;

import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.account.AccountProfileDTO;
import com.simfat.backend.dto.account.ChangePasswordRequestDTO;
import com.simfat.backend.dto.account.UpdateProfileRequestDTO;
import com.simfat.backend.security.SecurityUtils;
import com.simfat.backend.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AccountProfileDTO>> getProfile() {
        AccountProfileDTO data = accountService.getProfile(SecurityUtils.currentUserIdOrThrow());
        return ResponseEntity.ok(ApiResponse.ok("Perfil obtenido correctamente", data));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<AccountProfileDTO>> updateProfile(
        @Valid @RequestBody UpdateProfileRequestDTO request
    ) {
        AccountProfileDTO data = accountService.updateProfile(SecurityUtils.currentUserIdOrThrow(), request);
        return ResponseEntity.ok(ApiResponse.ok("Perfil actualizado correctamente", data));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
        @Valid @RequestBody ChangePasswordRequestDTO request
    ) {
        accountService.changePassword(SecurityUtils.currentUserIdOrThrow(), request);
        return ResponseEntity.ok(ApiResponse.ok(
            "Contrasena actualizada. Por seguridad se cerraron todas las sesiones activas.",
            null
        ));
    }
}
