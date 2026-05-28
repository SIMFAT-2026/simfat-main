package com.simfat.backend.dto.community.chat;

import java.time.LocalDateTime;

public record CommunityChatMessageDTO(
    String id,
    String roomId,
    String authorUserId,
    String authorName,
    String content,
    String status,
    LocalDateTime createdAt
) {
}
