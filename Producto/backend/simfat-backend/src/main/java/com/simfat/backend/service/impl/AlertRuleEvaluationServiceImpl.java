package com.simfat.backend.service.impl;

import com.simfat.backend.model.AlertRule;
import com.simfat.backend.model.ComunaRiskSnapshot;
import com.simfat.backend.service.AlertRuleEvaluationService;
import org.springframework.stereotype.Service;

@Service
public class AlertRuleEvaluationServiceImpl implements AlertRuleEvaluationService {

    @Override
    public boolean isThresholdExceeded(ComunaRiskSnapshot snapshot, AlertRule rule) {
        return (rule.getUmbralFwi() != null && snapshot.getFwiRaw() != null
                && snapshot.getFwiRaw() >= rule.getUmbralFwi())
            || (rule.getUmbralNdmi() != null && snapshot.getNdmiRaw() != null
                && snapshot.getNdmiRaw() <= rule.getUmbralNdmi())
            || (rule.getUmbralNdvi() != null && snapshot.getNdviRaw() != null
                && snapshot.getNdviRaw() <= rule.getUmbralNdvi())
            || (rule.getUmbralFirmsCount() != null && snapshot.getFirmsCount() != null
                && snapshot.getFirmsCount() >= rule.getUmbralFirmsCount())
            || (rule.getUmbralReportesCiudadanos() != null && snapshot.getReportsCount() != null
                && snapshot.getReportsCount() >= rule.getUmbralReportesCiudadanos());
    }
}
