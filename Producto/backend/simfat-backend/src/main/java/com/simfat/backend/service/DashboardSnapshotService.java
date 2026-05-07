package com.simfat.backend.service;

import com.simfat.backend.model.DashboardRegionSnapshot;

public interface DashboardSnapshotService {

    DashboardRegionSnapshot recomputeSnapshot(String regionId);
}
