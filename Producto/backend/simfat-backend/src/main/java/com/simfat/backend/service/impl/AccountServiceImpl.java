package com.simfat.backend.service.impl;

import com.simfat.backend.dto.account.AccountProfileDTO;
import com.simfat.backend.dto.account.ChangePasswordRequestDTO;
import com.simfat.backend.dto.account.UpdateProfileRequestDTO;
import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.model.AppUser;
import com.simfat.backend.model.UserCommunityProfile;
import com.simfat.backend.model.UserVerification;
import com.simfat.backend.model.VerificationEvent;
import com.simfat.backend.model.VerificationStatus;
import com.simfat.backend.repository.AppUserRepository;
import com.simfat.backend.repository.UserCommunityProfileRepository;
import com.simfat.backend.repository.UserVerificationRepository;
import com.simfat.backend.repository.VerificationEventRepository;
import com.simfat.backend.service.AccountService;
import com.simfat.backend.service.AuthService;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {

    private static final Set<VerificationStatus> IDENTITY_STATUSES = Set.of(
        VerificationStatus.IDENTITY_VERIFIED,
        VerificationStatus.FULLY_VERIFIED
    );

    private final AppUserRepository appUserRepository;
    private final UserVerificationRepository userVerificationRepository;
    private final VerificationEventRepository verificationEventRepository;
    private final UserCommunityProfileRepository userCommunityProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public AccountServiceImpl(
        AppUserRepository appUserRepository,
        UserVerificationRepository userVerificationRepository,
        VerificationEventRepository verificationEventRepository,
        UserCommunityProfileRepository userCommunityProfileRepository,
        PasswordEncoder passwordEncoder,
        AuthService authService
    ) {
        this.appUserRepository = appUserRepository;
        this.userVerificationRepository = userVerificationRepository;
        this.verificationEventRepository = verificationEventRepository;
        this.userCommunityProfileRepository = userCommunityProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountProfileDTO getProfile(String userId) {
        AppUser user = requireUser(userId);
        UserVerification verification = userVerificationRepository.findById(userId).orElse(null);
        return toProfileDTO(user, verification);
    }

    @Override
    @Transactional
    public AccountProfileDTO updateProfile(String userId, UpdateProfileRequestDTO request) {
        AppUser user = requireUser(userId);
        UserVerification verification = userVerificationRepository.findById(userId).orElse(null);

        if (request.fullName() != null && !request.fullName().equals(user.getFullName())) {
            if (verification != null && IDENTITY_STATUSES.contains(verification.getStatus())) {
                VerificationStatus oldStatus = verification.getStatus();
                verification.setStatus(VerificationStatus.EMAIL_VERIFIED);
                userVerificationRepository.save(verification);

                VerificationEvent event = new VerificationEvent();
                event.setUserId(userId);
                event.setEventType("IDENTITY_RESET");
                event.setOldStatus(oldStatus);
                event.setNewStatus(VerificationStatus.EMAIL_VERIFIED);
                event.setReviewedBy(userId);
                event.setNotes("Nombre cambiado por el propio usuario");
                verificationEventRepository.save(event);
            }
            user.setFullName(request.fullName());
        }

        if (request.phone() != null) {
            user.setPhone(request.phone().isBlank() ? null : request.phone());
        }
        if (request.regionCode() != null) {
            user.setRegionCode(request.regionCode().isBlank() ? null : request.regionCode());
        }
        if (request.comunaCode() != null) {
            user.setComunaCode(request.comunaCode().isBlank() ? null : request.comunaCode());
        }

        appUserRepository.save(user);

        if (request.regionCode() != null) {
            UserCommunityProfile communityProfile = userCommunityProfileRepository.findById(userId)
                .orElseGet(() -> {
                    UserCommunityProfile p = new UserCommunityProfile();
                    p.setUserId(userId);
                    return p;
                });
            communityProfile.setPrimaryRegionId(
                request.regionCode().isBlank() ? null : request.regionCode()
            );
            userCommunityProfileRepository.save(communityProfile);
        }

        UserVerification updatedVerification = userVerificationRepository.findById(userId).orElse(null);
        return toProfileDTO(user, updatedVerification);
    }

    @Override
    @Transactional
    public void changePassword(String userId, ChangePasswordRequestDTO request) {
        AppUser user = requireUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("La contrasena actual no es correcta");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("La nueva contrasena debe ser distinta a la actual");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("La confirmacion de contrasena no coincide");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        appUserRepository.save(user);

        authService.revokeAllTokens(userId);
    }

    private AppUser requireUser(String userId) {
        return appUserRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private AccountProfileDTO toProfileDTO(AppUser user, UserVerification verification) {
        Set<String> roles = user.getRoles().stream()
            .map(Enum::name)
            .collect(Collectors.toSet());

        String verificationStatus = verification != null ? verification.getStatus().name() : null;
        String organizationName = verification != null ? verification.getOrganizationName() : null;

        return new AccountProfileDTO(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getPhone(),
            user.getRegionCode(),
            user.getComunaCode(),
            organizationName,
            verificationStatus,
            roles,
            user.getCreatedAt()
        );
    }
}
