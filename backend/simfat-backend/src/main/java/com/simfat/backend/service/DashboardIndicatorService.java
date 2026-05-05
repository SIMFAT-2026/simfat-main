package com.simfat.backend.service;

import com.simfat.backend.dto.DataFreshnessDTO;
import com.simfat.backend.dto.IndicatorLatestDTO;
import com.simfat.backend.dto.IndicatorMapResponseDTO;
import com.simfat.backend.dto.IndicatorSeriesDTO;
import com.simfat.backend.model.IndicatorType;

public interface DashboardIndicatorService {

    IndicatorLatestDTO getLatest(String regionId, IndicatorType indicator);

    IndicatorSeriesDTO getSeries(String regionId, IndicatorType indicator, String from, String to, String granularity);

    IndicatorMapResponseDTO getMap(IndicatorType indicator, String from, String to, Integer limit);

    DataFreshnessDTO getDataFreshness(String regionId);
}
