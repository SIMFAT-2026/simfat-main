package com.simfat.backend.dto.admin;

import java.util.Set;

public record UpdateCommunityChatAccessRequestDTO(
    String primaryRegionId,
    Set<String> additionalRegionIds
) {
}
