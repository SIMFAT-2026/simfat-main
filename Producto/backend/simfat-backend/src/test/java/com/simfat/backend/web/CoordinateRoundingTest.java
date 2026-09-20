package com.simfat.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoordinateRoundingTest {

    @Test
    void nullStaysNull() {
        assertThat(CoordinateRounding.round(null)).isNull();
    }

    @Test
    void roundsToTwoDecimals() {
        assertThat(CoordinateRounding.round(-36.126)).isEqualTo(-36.13);
        assertThat(CoordinateRounding.round(-72.984)).isEqualTo(-72.98);
        assertThat(CoordinateRounding.round(12.3456)).isEqualTo(12.35);
    }

    @Test
    void halfwayBoundaryRoundsUpTowardPositiveInfinity() {
        assertThat(CoordinateRounding.round(0.005)).isEqualTo(0.01);
        assertThat(CoordinateRounding.round(0.004)).isEqualTo(0.0);
        assertThat(CoordinateRounding.round(-0.006)).isEqualTo(-0.01);
    }

    @Test
    void smallNegativeValuesNormaliseNegativeZeroToPositiveZero() {
        Double result = CoordinateRounding.round(-0.004);
        assertThat(result).isEqualTo(0.0);
        assertThat(Double.doubleToRawLongBits(result)).isEqualTo(Double.doubleToRawLongBits(0.0));
        assertThat(Double.doubleToRawLongBits(CoordinateRounding.round(-0.0))).isEqualTo(Double.doubleToRawLongBits(0.0));
    }

    @Test
    void alreadyRoundedValuesAreUnchanged() {
        assertThat(CoordinateRounding.round(-33.45)).isEqualTo(-33.45);
        assertThat(CoordinateRounding.round(0.0)).isEqualTo(0.0);
    }
}
