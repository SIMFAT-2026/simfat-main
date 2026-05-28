package com.simfat.backend.service;

import com.simfat.backend.dto.community.chat.CommunityChatActorDTO;
import com.simfat.backend.dto.community.chat.CommunityChatMessageDTO;
import com.simfat.backend.dto.community.chat.CommunityChatMessageRequestDTO;
import com.simfat.backend.dto.community.chat.CommunityChatModerationRequestDTO;
import com.simfat.backend.dto.community.chat.CommunityChatPresenceRequestDTO;
import com.simfat.backend.dto.community.chat.CommunityChatRoomDTO;
import java.time.LocalDateTime;
import java.util.List;

public interface CommunityChatService {

    List<CommunityChatRoomDTO> listRooms(CommunityChatActorDTO actor);

    List<CommunityChatMessageDTO> listMessages(String roomId, LocalDateTime after, int limit, CommunityChatActorDTO actor);

    CommunityChatMessageDTO sendMessage(String roomId, CommunityChatMessageRequestDTO request, CommunityChatActorDTO actor);

    void updatePresence(CommunityChatPresenceRequestDTO request, CommunityChatActorDTO actor);

    CommunityChatMessageDTO moderateMessage(String messageId, CommunityChatModerationRequestDTO request, CommunityChatActorDTO actor);

    long cleanupExpiredMessages(int activeRegionCount);
}
