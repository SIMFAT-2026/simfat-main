package com.simfat.backend.service;

import com.simfat.backend.dto.SyncRunResponseDTO;

public interface OpenEoSyncService {

    SyncRunResponseDTO runSync(String regionId);

    default SyncRunResponseDTO runSync(String regionId, String from, String to) {
        return runSync(regionId);
    }
}
