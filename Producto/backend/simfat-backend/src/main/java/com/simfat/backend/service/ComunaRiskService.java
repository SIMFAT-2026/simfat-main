package com.simfat.backend.service;

import com.simfat.backend.model.ComunaRiskSnapshot;
import java.util.List;
import java.util.Map;

public interface ComunaRiskService {

    void recomputeAllComunas();

    ComunaRiskSnapshot recomputeByComuna(String comunaId);

    Map<String, ComunaRiskSnapshot> getLatestSnapshotsByRegion(String regionId);

    List<ComunaRiskSnapshot> getLatestSnapshotsListByRegion(String regionId);

    List<ComunaRiskSnapshot> getSnapshotHistory(String gadmGid, int days);

    ComunaRiskSnapshot syncCopernicusAndRecompute(String comunaId);
}
