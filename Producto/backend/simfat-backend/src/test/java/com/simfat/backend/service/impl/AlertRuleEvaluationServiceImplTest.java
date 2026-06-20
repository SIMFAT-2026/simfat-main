package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.simfat.backend.model.AlertRule;
import com.simfat.backend.model.ComunaRiskSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlertRuleEvaluationServiceImplTest {

    private AlertRuleEvaluationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AlertRuleEvaluationServiceImpl();
    }

    @Test
    void isThresholdExceeded_noVariablesConfigured_returnsFalse() {
        ComunaRiskSnapshot snapshot = new ComunaRiskSnapshot();
        snapshot.setFwiRaw(40.0);
        snapshot.setNdmiRaw(-0.5);
        snapshot.setNdviRaw(0.2);
        snapshot.setFirmsCount(10);
        snapshot.setReportsCount(5);

        AlertRule rule = new AlertRule();

        assertFalse(service.isThresholdExceeded(snapshot, rule));
    }

    @Test
    void isThresholdExceeded_fwiExceedsThreshold_returnsTrue() {
        ComunaRiskSnapshot snapshot = new ComunaRiskSnapshot();
        snapshot.setFwiRaw(30.0);

        AlertRule rule = new AlertRule();
        rule.setUmbralFwi(25.0);

        assertTrue(service.isThresholdExceeded(snapshot, rule));
    }

    @Test
    void isThresholdExceeded_ndmiBelowOrEqualThreshold_returnsTrue() {
        ComunaRiskSnapshot snapshot = new ComunaRiskSnapshot();
        snapshot.setNdmiRaw(-0.4);

        AlertRule rule = new AlertRule();
        rule.setUmbralNdmi(-0.2);

        assertTrue(service.isThresholdExceeded(snapshot, rule));
    }

    @Test
    void isThresholdExceeded_variablesConfiguredButNoneExceeded_returnsFalse() {
        ComunaRiskSnapshot snapshot = new ComunaRiskSnapshot();
        snapshot.setFwiRaw(10.0);
        snapshot.setNdmiRaw(0.5);
        snapshot.setNdviRaw(0.8);
        snapshot.setFirmsCount(1);
        snapshot.setReportsCount(0);

        AlertRule rule = new AlertRule();
        rule.setUmbralFwi(25.0);
        rule.setUmbralNdmi(-0.2);
        rule.setUmbralNdvi(0.3);
        rule.setUmbralFirmsCount(5);
        rule.setUmbralReportesCiudadanos(3);

        assertFalse(service.isThresholdExceeded(snapshot, rule));
    }

    @Test
    void isThresholdExceeded_nullNdmiAndNdviInSnapshot_doesNotThrowAndDoesNotCountAsExceeded_butOtherVariableStillTriggersOr() {
        ComunaRiskSnapshot snapshot = new ComunaRiskSnapshot();
        snapshot.setNdmiRaw(null);
        snapshot.setNdviRaw(null);
        snapshot.setFirmsCount(20);

        AlertRule rule = new AlertRule();
        rule.setUmbralNdmi(-0.2);
        rule.setUmbralNdvi(0.3);
        rule.setUmbralFirmsCount(10);

        assertTrue(service.isThresholdExceeded(snapshot, rule));
    }

    @Test
    void isThresholdExceeded_nullNdmiAndNdviInSnapshot_andNoOtherVariableExceeded_returnsFalse() {
        ComunaRiskSnapshot snapshot = new ComunaRiskSnapshot();
        snapshot.setNdmiRaw(null);
        snapshot.setNdviRaw(null);

        AlertRule rule = new AlertRule();
        rule.setUmbralNdmi(-0.2);
        rule.setUmbralNdvi(0.3);

        assertFalse(service.isThresholdExceeded(snapshot, rule));
    }
}
