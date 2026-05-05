package com.simfat.backend.service;

import com.simfat.backend.dto.AlertsSummaryDTO;
import com.simfat.backend.dto.CriticalRegionDTO;
import com.simfat.backend.dto.DashboardSummaryDTO;
import com.simfat.backend.dto.LossTrendPointDTO;
import java.util.List;

public interface DashboardService {

    DashboardSummaryDTO getSummary();

    List<CriticalRegionDTO> getCriticalRegions();

    List<LossTrendPointDTO> getLossTrend();

    AlertsSummaryDTO getAlertsSummary();
}

