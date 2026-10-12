package com.simfat.backend.service.fwi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Approval tests for {@link LegacyProxyFwiCalculator#computeProxyFwi}, moved verbatim from
 * {@code OpenWeatherFwiServiceImpl.computeProxyFwi} during S1d2. Expected values are computed by
 * hand from the exact same formula the method implements, proving the move introduced no
 * behavior change (CFW-4b).
 */
class LegacyProxyFwiCalculatorTest {

    @Test
    void computeProxyFwi_moderateHeatAndWindNoRain_matchesHandComputedFormula() {
        // tempFactor = min(1, 28.5/40) = 0.7125
        // drynessFactor = (100-35)/100 = 0.65
        // windFactor = min(1, 20/60) = 0.333333333...
        // rainFactor = max(0, 1-0/3) = 1.0
        // raw = 60 * (0.40*0.65 + 0.30*0.7125 + 0.30*0.333333333) * 1.0 = 34.425
        double result = LegacyProxyFwiCalculator.computeProxyFwi(28.5, 35.0, 20.0, 0.0);
        assertEquals(34.425, result, 1e-9);
    }

    @Test
    void computeProxyFwi_heavyRain_dampensToZeroRegardlessOfOtherFactors() {
        // rainFactor = max(0, 1 - 10/3) = max(0, -2.333) = 0 -> raw = 0
        double result = LegacyProxyFwiCalculator.computeProxyFwi(35.0, 10.0, 80.0, 10.0);
        assertEquals(0.0, result, 1e-9);
    }

    @Test
    void computeProxyFwi_extremeInputsAboveClampBounds_capsAtSixty() {
        // tempFactor = min(1, 45/40) = 1; drynessFactor = (100-0)/100 = 1;
        // windFactor = min(1, 80/60) = 1; rainFactor = 1
        // raw = 60 * (0.40 + 0.30 + 0.30) = 60.0 (already at the upper clamp)
        double result = LegacyProxyFwiCalculator.computeProxyFwi(45.0, 0.0, 80.0, 0.0);
        assertEquals(60.0, result, 1e-9);
    }
}
