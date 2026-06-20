package com.simfat.backend.service;

import com.simfat.backend.model.TerritoryRiskSnapshot;

public interface TerritoryRiskService {

    void recomputeRiskForAllRegions();

    TerritoryRiskSnapshot recomputeRiskByRegion(String regionId);

    TerritoryRiskSnapshot getLatestSnapshot(String regionId);
}
