package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.simfat.backend.dto.admin.UpdateCommunityChatAccessRequestDTO;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.model.AppUser;
import com.simfat.backend.model.CommunityChatRoomAccess;
import com.simfat.backend.model.UserCommunityProfile;
import com.simfat.backend.repository.AppPermissionRepository;
import com.simfat.backend.repository.AppRoleRepository;
import com.simfat.backend.repository.AppUserRepository;
import com.simfat.backend.repository.CommunityChatRoomAccessRepository;
import com.simfat.backend.repository.UserCommunityProfileRepository;
import com.simfat.backend.repository.UserRoleAssignmentRepository;
import com.simfat.backend.repository.UserVerificationRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessAdminServiceImplTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private AppRoleRepository appRoleRepository;
    @Mock
    private AppPermissionRepository appPermissionRepository;
    @Mock
    private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Mock
    private UserVerificationRepository userVerificationRepository;
    @Mock
    private UserCommunityProfileRepository userCommunityProfileRepository;
    @Mock
    private CommunityChatRoomAccessRepository communityChatRoomAccessRepository;

    private AccessAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccessAdminServiceImpl(
            appUserRepository,
            appRoleRepository,
            appPermissionRepository,
            userRoleAssignmentRepository,
            userVerificationRepository,
            userCommunityProfileRepository,
            communityChatRoomAccessRepository
        );
    }

    @Test
    void updateCommunityChatAccess_savesPrimaryRegionAndExtraRegionalGrants() {
        AppUser user = user("user-1");
        when(appUserRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userCommunityProfileRepository.save(any(UserCommunityProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(communityChatRoomAccessRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRoleAssignmentRepository.findAll()).thenReturn(List.of());
        when(userVerificationRepository.findById("user-1")).thenReturn(Optional.empty());
        when(communityChatRoomAccessRepository.findByUserIdAndRevokedAtIsNull("user-1")).thenReturn(List.of(
            access("user-1", "araucania"),
            access("user-1", "los-rios")
        ));

        var result = service.updateCommunityChatAccess(
            "user-1",
            new UpdateCommunityChatAccessRequestDTO("biobio", Set.of("araucania", "los-rios")),
            "admin-1"
        );

        assertEquals("biobio", result.communityChatAccess().primaryRegionId());
        assertEquals(Set.of("araucania", "los-rios"), result.communityChatAccess().additionalRegionIds());
        verify(communityChatRoomAccessRepository).revokeActiveByUserId("user-1");
        verify(userCommunityProfileRepository).save(any(UserCommunityProfile.class));
    }

    @Test
    void updateCommunityChatAccess_rejectsUnknownUser() {
        when(appUserRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(
            ResourceNotFoundException.class,
            () -> service.updateCommunityChatAccess(
                "missing",
                new UpdateCommunityChatAccessRequestDTO("biobio", Set.of()),
                "admin-1"
            )
        );
    }

    private AppUser user(String id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail(id + "@example.com");
        user.setFullName("User " + id);
        user.setEnabled(true);
        return user;
    }

    private CommunityChatRoomAccess access(String userId, String regionId) {
        CommunityChatRoomAccess access = new CommunityChatRoomAccess();
        access.setUserId(userId);
        access.setRegionId(regionId);
        return access;
    }
}
