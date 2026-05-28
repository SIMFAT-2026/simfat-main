package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.simfat.backend.dto.community.chat.CommunityChatActorDTO;
import com.simfat.backend.dto.community.chat.CommunityChatMessageRequestDTO;
import com.simfat.backend.dto.community.chat.CommunityChatModerationRequestDTO;
import com.simfat.backend.dto.community.chat.CommunityChatPresenceRequestDTO;
import com.simfat.backend.exception.ForbiddenException;
import com.simfat.backend.model.CommunityChatMessage;
import com.simfat.backend.model.CommunityChatPresenceState;
import com.simfat.backend.model.CommunityChatRoom;
import com.simfat.backend.model.CommunityChatRoomAccess;
import com.simfat.backend.model.CommunityChatRoomType;
import com.simfat.backend.model.UserCommunityProfile;
import com.simfat.backend.model.UserVerification;
import com.simfat.backend.model.VerificationStatus;
import com.simfat.backend.repository.CommunityChatMessageRepository;
import com.simfat.backend.repository.CommunityChatModerationEventRepository;
import com.simfat.backend.repository.CommunityChatPresenceRepository;
import com.simfat.backend.repository.CommunityChatRoomAccessRepository;
import com.simfat.backend.repository.CommunityChatRoomRepository;
import com.simfat.backend.repository.UserCommunityProfileRepository;
import com.simfat.backend.repository.UserVerificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommunityChatServiceImplTest {

    @Mock
    private CommunityChatRoomRepository roomRepository;
    @Mock
    private CommunityChatMessageRepository messageRepository;
    @Mock
    private CommunityChatPresenceRepository presenceRepository;
    @Mock
    private CommunityChatModerationEventRepository moderationEventRepository;
    @Mock
    private UserVerificationRepository userVerificationRepository;
    @Mock
    private UserCommunityProfileRepository userCommunityProfileRepository;
    @Mock
    private CommunityChatRoomAccessRepository roomAccessRepository;

    private CommunityChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommunityChatServiceImpl(
            roomRepository,
            messageRepository,
            presenceRepository,
            moderationEventRepository,
            userVerificationRepository,
            userCommunityProfileRepository,
            roomAccessRepository
        );
    }

    @Test
    void listRooms_filtersRegionalRoomsForVerifiedCommunityUser() {
        when(roomRepository.findByActiveTrueOrderByTypeAscNameAsc()).thenReturn(List.of(
            room("general", CommunityChatRoomType.GENERAL, null),
            room("biobio", CommunityChatRoomType.REGION, "biobio"),
            room("araucania", CommunityChatRoomType.REGION, "araucania")
        ));
        when(userVerificationRepository.findById("user-1")).thenReturn(Optional.of(verification(VerificationStatus.IDENTITY_VERIFIED)));
        when(userCommunityProfileRepository.findById("user-1")).thenReturn(Optional.of(profile("user-1", "biobio")));
        when(roomAccessRepository.findByUserIdAndRevokedAtIsNull("user-1")).thenReturn(List.of(access("araucania")));

        var rooms = service.listRooms(communityActor());

        assertEquals(List.of("general", "biobio", "araucania"), rooms.stream().map(room -> room.id()).toList());
    }

    @Test
    void listRooms_seedsDefaultRoomsWhenNoneExist() {
        when(roomRepository.findByActiveTrueOrderByTypeAscNameAsc()).thenReturn(List.of());
        when(roomRepository.saveAll(any())).thenReturn(List.of(
            room("general", CommunityChatRoomType.GENERAL, null),
            room("biobio", CommunityChatRoomType.REGION, "biobio"),
            room("araucania", CommunityChatRoomType.REGION, "araucania")
        ));

        var rooms = service.listRooms(new CommunityChatActorDTO("admin-1", "Admin", Set.of("ROLE_ADMIN")));

        assertEquals(List.of("general", "biobio", "araucania"), rooms.stream().map(room -> room.id()).toList());
        verify(roomRepository).saveAll(any());
    }

    @Test
    void listRooms_rejectsUnverifiedCommunityUser() {
        when(userVerificationRepository.findById("user-1")).thenReturn(Optional.of(verification(VerificationStatus.UNVERIFIED)));

        assertThrows(ForbiddenException.class, () -> service.listRooms(communityActor()));
    }

    @Test
    void sendMessage_storesTraceableAuthorReference() {
        CommunityChatRoom general = room("general", CommunityChatRoomType.GENERAL, null);
        when(roomRepository.findById("general")).thenReturn(Optional.of(general));
        when(userVerificationRepository.findById("user-1")).thenReturn(Optional.of(verification(VerificationStatus.FULLY_VERIFIED)));
        when(messageRepository.save(any(CommunityChatMessage.class))).thenAnswer(invocation -> {
            CommunityChatMessage message = invocation.getArgument(0);
            message.setId("msg-1");
            return message;
        });

        var response = service.sendMessage("general", new CommunityChatMessageRequestDTO("Prevencion activa"), communityActor());

        assertEquals("msg-1", response.id());
        assertEquals("user-1", response.authorUserId());
        assertEquals("Ana Brigadista", response.authorName());
    }

    @Test
    void updatePresence_savesRequestedState() {
        when(roomRepository.findById("general")).thenReturn(Optional.of(room("general", CommunityChatRoomType.GENERAL, null)));
        when(userVerificationRepository.findById("user-1")).thenReturn(Optional.of(verification(VerificationStatus.IDENTITY_VERIFIED)));

        service.updatePresence(new CommunityChatPresenceRequestDTO("general", CommunityChatPresenceState.AWAY), communityActor());

        verify(presenceRepository).save(any());
    }

    @Test
    void moderateMessage_requiresOperationalRole() {
        assertThrows(
            ForbiddenException.class,
            () -> service.moderateMessage("msg-1", new CommunityChatModerationRequestDTO("DELETE", "spam"), communityActor())
        );
    }

    @Test
    void retentionCutoff_changesAtSixRegions() {
        assertEquals(LocalDateTime.now().minusMonths(6).getMonth(), service.retentionCutoff(2).getMonth());
        assertEquals(LocalDateTime.now().minusMonths(1).getMonth(), service.retentionCutoff(6).getMonth());
    }

    private CommunityChatActorDTO communityActor() {
        return new CommunityChatActorDTO("user-1", "Ana Brigadista", Set.of("ROLE_COMMUNITY_USER", "ROLE_VERIFIED_USER"));
    }

    private CommunityChatRoom room(String id, CommunityChatRoomType type, String regionId) {
        CommunityChatRoom room = new CommunityChatRoom();
        room.setId(id);
        room.setName(id);
        room.setType(type);
        room.setRegionId(regionId);
        room.setActive(true);
        return room;
    }

    private CommunityChatMessage message(String id, String roomId) {
        CommunityChatMessage message = new CommunityChatMessage();
        message.setId(id);
        message.setRoomId(roomId);
        message.setAuthorUserId("user-2");
        message.setContent("content");
        return message;
    }

    private UserVerification verification(VerificationStatus status) {
        UserVerification verification = new UserVerification();
        verification.setUserId("user-1");
        verification.setStatus(status);
        return verification;
    }

    private UserCommunityProfile profile(String userId, String regionId) {
        UserCommunityProfile profile = new UserCommunityProfile();
        profile.setUserId(userId);
        profile.setPrimaryRegionId(regionId);
        return profile;
    }

    private CommunityChatRoomAccess access(String regionId) {
        CommunityChatRoomAccess access = new CommunityChatRoomAccess();
        access.setUserId("user-1");
        access.setRegionId(regionId);
        return access;
    }
}
