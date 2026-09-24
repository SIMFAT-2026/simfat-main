package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * S2a2 task 2a.4 (MRB-3b): fail-fast validation of {@code territory.riesgo.mapbiomas.weight}.
 *
 * <p>Exercises {@link ComunaRiskServiceImpl#validateMapbiomasWeight()} -- a package-private
 * {@code @PostConstruct} method -- directly, so the grid check is unit-tested WITHOUT spinning
 * up a Spring context (no Mongo is available in this environment, and every other collaborator
 * this class needs is irrelevant to weight validation). A real application context invokes the
 * same method via the bean lifecycle and wraps any thrown exception in a
 * {@code BeanCreationException}; that wrapping is Spring's own well-tested behaviour and is out
 * of scope here -- what matters is that construction-time validation exists and rejects the
 * right values with a clear message (MRB-3b), while never rejecting the safe default or any
 * pre-registered grid value (MRB-3a/3c).
 */
class MapbiomasWeightConfigTest {

    private static ComunaRiskServiceImpl newServiceWithWeight(Double weight) {
        ComunaRiskServiceImpl service = new ComunaRiskServiceImpl(
            null, null, null, null, null, null, null, null, null, null
        );
        if (weight != null) {
            service.setMapbiomasWeight(weight);
        }
        return service;
    }

    @Test
    void absentProperty_defaultsToZero_andIsValid() {
        // No setMapbiomasWeight call: the field keeps its @Value("${...:0.0}") default (MRB-3a).
        ComunaRiskServiceImpl service = newServiceWithWeight(null);

        assertDoesNotThrow(service::validateMapbiomasWeight);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.10, 0.25, 0.35, 0.50})
    void gridValues_startCleanly(double wM) {
        ComunaRiskServiceImpl service = newServiceWithWeight(wM);

        assertDoesNotThrow(service::validateMapbiomasWeight);
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, -0.1, 0.9, 0.30})
    void invalidValues_failFastWithClearMessage(double wM) {
        ComunaRiskServiceImpl service = newServiceWithWeight(wM);

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateMapbiomasWeight);

        assertTrue(ex.getMessage().contains("territory.riesgo.mapbiomas.weight"),
            "message should name the offending property: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("0.1") || ex.getMessage().contains("0.25")
                || ex.getMessage().contains("0.35") || ex.getMessage().contains("0.5"),
            "message should list the valid grid so an operator can fix it: " + ex.getMessage());
    }
}
