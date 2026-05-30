package com.simfat.backend.dto.admin;

import java.util.Set;

public record CommunityChatAccessDTO(
    String primaryRegionId,
    Set<String> additionalRegionIds
) {
}
