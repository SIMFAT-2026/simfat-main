package com.simfat.backend.service;

import com.simfat.backend.model.AlertRule;
import com.simfat.backend.model.ComunaRiskSnapshot;

public interface AlertRuleEvaluationService {

    boolean isThresholdExceeded(ComunaRiskSnapshot snapshot, AlertRule rule);
}
