package com.simfat.backend.controller;

import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.auth.AuthTokensResponseDTO;
import com.simfat.backend.dto.auth.AuthUserDTO;
import com.simfat.backend.dto.auth.ForgotPasswordRequestDTO;
import com.simfat.backend.dto.auth.LoginRequestDTO;
import com.simfat.backend.dto.auth.LogoutRequestDTO;
import com.simfat.backend.dto.auth.RefreshTokenRequestDTO;
import com.simfat.backend.dto.auth.RegisterRequestDTO;
import com.simfat.backend.dto.auth.ResetPasswordRequestDTO;
import com.simfat.backend.dto.auth.SeedUsersRequestDTO;
import com.simfat.backend.dto.auth.SeedUsersResponseDTO;
import com.simfat.backend.security.SecurityUtils;
import com.simfat.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthTokensResponseDTO>> register(
        @Valid @RequestBody RegisterRequestDTO request,
        HttpServletRequest httpRequest
    ) {
        AuthTokensResponseDTO data = authService.register(request, clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.ok("Registro exitoso", data));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokensResponseDTO>> login(
        @Valid @RequestBody LoginRequestDTO request,
        HttpServletRequest httpRequest
    ) {
        AuthTokensResponseDTO data = authService.login(request, clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.ok("Inicio de sesion exitoso", data));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokensResponseDTO>> refresh(
        @Valid @RequestBody RefreshTokenRequestDTO request,
        HttpServletRequest httpRequest
    ) {
        AuthTokensResponseDTO data = authService.refresh(request, clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.ok("Token renovado", data));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthUserDTO>> me() {
        AuthUserDTO data = authService.getCurrentUser(SecurityUtils.currentUserIdOrThrow());
        return ResponseEntity.ok(ApiResponse.ok("Perfil obtenido correctamente", data));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) LogoutRequestDTO request) {
        authService.logout(SecurityUtils.currentUserIdOrThrow(), request);
        return ResponseEntity.ok(ApiResponse.ok("Sesion cerrada", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
        @Valid @RequestBody ForgotPasswordRequestDTO request,
        HttpServletRequest httpRequest
    ) {
        authService.forgotPassword(request, clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok(
            "Si el correo esta registrado, enviaremos instrucciones para restablecer la contrasena",
            null
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Contrasena actualizada correctamente", null));
    }

    @PostMapping("/dev/seed-users")
    public ResponseEntity<ApiResponse<SeedUsersResponseDTO>> seedUsers(@Valid @RequestBody SeedUsersRequestDTO request) {
        SeedUsersResponseDTO data = authService.seedUsers(request.count());
        return ResponseEntity.ok(ApiResponse.ok("Usuarios de prueba creados correctamente", data));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

