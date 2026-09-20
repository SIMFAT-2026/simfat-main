package com.simfat.backend.web;

/** Shared coordinate rounding for anonymous payloads (about 1 km precision at two decimals). */
public final class CoordinateRounding {

    private CoordinateRounding() {
    }

    /** Rounds to 2 decimals (half up); null stays null and negative zero is normalised to 0.0. */
    public static Double round(Double value) {
        if (value == null) {
            return null;
        }
        // "+ 0.0" turns -0.0 into 0.0 (IEEE 754), so the JSON never shows "-0.0".
        return Math.round(value * 100.0) / 100.0 + 0.0;
    }
}
