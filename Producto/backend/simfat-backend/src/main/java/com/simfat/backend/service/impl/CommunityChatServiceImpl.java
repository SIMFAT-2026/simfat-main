package com.simfat.backend.service.impl;

import com.simfat.backend.dto.community.chat.CommunityChatActorDTO;
import com.simfat.backend.dto.community.chat.CommunityChatMessageDTO;
import com.simfat.backend.dto.community.chat.CommunityChatMessageRequestDTO;
import com.simfat.backend.dto.community.chat.CommunityChatModerationRequestDTO;
import com.simfat.backend.dto.community.chat.CommunityChatPresenceRequestDTO;
import com.simfat.backend.dto.community.chat.CommunityChatRoomDTO;
import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.exception.ForbiddenException;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.model.CommunityChatMessage;
import com.simfat.backend.model.CommunityChatMessageStatus;
import com.simfat.backend.model.CommunityChatModerationEvent;
import com.simfat.backend.model.CommunityChatPresence;
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
import com.simfat.backend.service.CommunityChatService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class CommunityChatServiceImpl implements CommunityChatService {

    private static final int REGION_SCALE_THRESHOLD = 6;
    private static final int DEFAULT_LIMIT = 50;
    private static final Set<String> OPERATIONAL_ROLES = Set.of("ROLE_MODERATOR", "ROLE_ADMIN", "ROLE_SUPER_ADMIN");

    private final CommunityChatRoomRepository roomRepository;
    private final CommunityChatMessageRepository messageRepository;
    private final CommunityChatPresenceRepository presenceRepository;
    private final CommunityChatModerationEventRepository moderationEventRepository;
    private final UserVerificationRepository userVerificationRepository;
    private final UserCommunityProfileRepository userCommunityProfileRepository;
    private final CommunityChatRoomAccessRepository roomAccessRepository;

    public CommunityChatServiceImpl(
        CommunityChatRoomRepository roomRepository,
        CommunityChatMessageRepository messageRepository,
        CommunityChatPresenceRepository presenceRepository,
        CommunityChatModerationEventRepository moderationEventRepository,
        UserVerificationRepository userVerificationRepository,
        UserCommunityProfileRepository userCommunityProfileRepository,
        CommunityChatRoomAccessRepository roomAccessRepository
    ) {
        this.roomRepository = roomRepository;
        this.messageRepository = messageRepository;
        this.presenceRepository = presenceRepository;
        this.moderationEventRepository = moderationEventRepository;
        this.userVerificationRepository = userVerificationRepository;
        this.userCommunityProfileRepository = userCommunityProfileRepository;
        this.roomAccessRepository = roomAccessRepository;
    }

    @Override
    public List<CommunityChatRoomDTO> listRooms(CommunityChatActorDTO actor) {
        ensureChatAccess(actor);
        List<CommunityChatRoom> rooms = activeRooms();
        return rooms.stream()
            .filter(room -> canAccessRoom(actor, room))
            .map(this::toRoomDTO)
            .toList();
    }

    @Override
    public List<CommunityChatMessageDTO> listMessages(String roomId, LocalDateTime after, int limit, CommunityChatActorDTO actor) {
        CommunityChatRoom room = findRoom(roomId);
        ensureRoomAccess(actor, room);
        PageRequest page = PageRequest.of(0, normalizeLimit(limit));
        List<CommunityChatMessage> messages = after == null
            ? messageRepository.findByRoomIdOrderByCreatedAtAsc(roomId, page)
            : messageRepository.findByRoomIdAndCreatedAtAfterOrderByCreatedAtAsc(roomId, after, page);
        return messages.stream().map(message -> toMessageDTO(message, "")).toList();
    }

    @Override
    public CommunityChatMessageDTO sendMessage(String roomId, CommunityChatMessageRequestDTO request, CommunityChatActorDTO actor) {
        CommunityChatRoom room = findRoom(roomId);
        ensureRoomAccess(actor, room);
        String content = request == null ? "" : request.content();
        if (content == null || content.isBlank()) {
            throw new BadRequestException("El mensaje es obligatorio");
        }

        CommunityChatMessage message = new CommunityChatMessage();
        message.setRoomId(room.getId());
        message.setAuthorUserId(actor.userId());
        message.setContent(content.trim());
        message.setStatus(CommunityChatMessageStatus.ACTIVE);
        message.setCreatedAt(LocalDateTime.now());

        CommunityChatMessage saved = messageRepository.save(message);
        return toMessageDTO(saved, actor.fullName());
    }

    @Override
    public void updatePresence(CommunityChatPresenceRequestDTO request, CommunityChatActorDTO actor) {
        CommunityChatRoom room = findRoom(request.roomId());
        ensureRoomAccess(actor, room);
        CommunityChatPresence presence = presenceRepository
            .findByUserIdAndRoomId(actor.userId(), room.getId())
            .orElseGet(CommunityChatPresence::new);
        presence.setUserId(actor.userId());
        presence.setRoomId(room.getId());
        presence.setState(request.state());
        presence.setLastSeenAt(LocalDateTime.now());
        presenceRepository.save(presence);
    }

    @Override
    public CommunityChatMessageDTO moderateMessage(String messageId, CommunityChatModerationRequestDTO request, CommunityChatActorDTO actor) {
        if (!isOperational(actor)) {
            throw new ForbiddenException("No tienes permisos para moderar el chat");
        }
        CommunityChatMessage message = messageRepository.findById(messageId)
            .orElseThrow(() -> new ResourceNotFoundException("Mensaje no encontrado"));
        message.setStatus(CommunityChatMessageStatus.DELETED);
        message.setModeratedAt(LocalDateTime.now());
        CommunityChatMessage saved = messageRepository.save(message);

        CommunityChatModerationEvent event = new CommunityChatModerationEvent();
        event.setMessageId(messageId);
        event.setAction(request == null ? "DELETE" : request.action());
        event.setActorUserId(actor.userId());
        event.setReason(request == null ? "" : request.reason());
        event.setCreatedAt(LocalDateTime.now());
        moderationEventRepository.save(event);

        return toMessageDTO(saved, "");
    }

    @Override
    public long cleanupExpiredMessages(int activeRegionCount) {
        return messageRepository.deleteByCreatedAtBefore(retentionCutoff(activeRegionCount));
    }

    public LocalDateTime retentionCutoff(int activeRegionCount) {
        return activeRegionCount >= REGION_SCALE_THRESHOLD ? LocalDateTime.now().minusMonths(1) : LocalDateTime.now().minusMonths(6);
    }

    private void ensureRoomAccess(CommunityChatActorDTO actor, CommunityChatRoom room) {
        ensureChatAccess(actor);
        if (!canAccessRoom(actor, room)) {
            throw new ForbiddenException("No tienes acceso a esta sala regional");
        }
    }

    private void ensureChatAccess(CommunityChatActorDTO actor) {
        if (isOperational(actor)) {
            return;
        }
        UserVerification verification = userVerificationRepository.findById(actor.userId()).orElse(null);
        VerificationStatus status = verification == null ? VerificationStatus.UNVERIFIED : verification.getStatus();
        if (status != VerificationStatus.IDENTITY_VERIFIED && status != VerificationStatus.FULLY_VERIFIED) {
            throw new ForbiddenException("Debes tener identidad verificada para usar el chat comunitario");
        }
    }

    private boolean canAccessRoom(CommunityChatActorDTO actor, CommunityChatRoom room) {
        if (isOperational(actor) || room.getType() == CommunityChatRoomType.GENERAL) {
            return true;
        }
        String regionId = normalize(room.getRegionId());
        if (regionId.isBlank()) {
            return false;
        }
        UserCommunityProfile profile = userCommunityProfileRepository.findById(actor.userId()).orElse(null);
        if (profile != null && regionId.equals(normalize(profile.getPrimaryRegionId()))) {
            return true;
        }
        Set<String> extraRegions = roomAccessRepository.findByUserIdAndRevokedAtIsNull(actor.userId()).stream()
            .map(CommunityChatRoomAccess::getRegionId)
            .map(this::normalize)
            .collect(Collectors.toSet());
        return extraRegions.contains(regionId);
    }

    private boolean isOperational(CommunityChatActorDTO actor) {
        return actor != null && actor.roles() != null && actor.roles().stream().anyMatch(OPERATIONAL_ROLES::contains);
    }

    private CommunityChatRoom findRoom(String roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new ResourceNotFoundException("Sala de chat no encontrada"));
    }

    private List<CommunityChatRoom> activeRooms() {
        List<CommunityChatRoom> rooms = roomRepository.findByActiveTrueOrderByTypeAscNameAsc();
        if (!rooms.isEmpty()) {
            return rooms;
        }
        return roomRepository.saveAll(List.of(
            defaultRoom("general", CommunityChatRoomType.GENERAL, null, "Sala general comunitaria"),
            defaultRoom("biobio", CommunityChatRoomType.REGION, "biobio", "Biobio"),
            defaultRoom("araucania", CommunityChatRoomType.REGION, "araucania", "La Araucania")
        ));
    }

    private CommunityChatRoom defaultRoom(String id, CommunityChatRoomType type, String regionId, String name) {
        CommunityChatRoom room = new CommunityChatRoom();
        room.setId(id);
        room.setType(type);
        room.setRegionId(regionId);
        room.setName(name);
        room.setActive(true);
        room.setCreatedAt(LocalDateTime.now());
        return room;
    }

    private CommunityChatRoomDTO toRoomDTO(CommunityChatRoom room) {
        return new CommunityChatRoomDTO(
            room.getId(),
            room.getType() == null ? "" : room.getType().name(),
            room.getRegionId(),
            room.getName()
        );
    }

    private CommunityChatMessageDTO toMessageDTO(CommunityChatMessage message, String authorName) {
        return new CommunityChatMessageDTO(
            message.getId(),
            message.getRoomId(),
            message.getAuthorUserId(),
            authorName,
            message.getContent(),
            message.getStatus() == null ? "" : message.getStatus().name(),
            message.getCreatedAt()
        );
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, DEFAULT_LIMIT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
