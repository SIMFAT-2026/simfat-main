package com.simfat.backend.service.fwi;

/**
 * The three FWI-scale alert thresholds {@code ComunaRiskServiceImpl} normalizes/overrides
 * against ({@code FWI_PREVENTIVO}, {@code FWI_CRITICO}, {@code FWI_MAX}), plus provenance
 * metadata that lets a caller tell whether the numbers are real or a placeholder.
 *
 * @param preventivo FWI value at/above which the PREVENTIVO alert level starts to apply.
 * @param critico FWI value at/above which the FWI-driven CRITICO/ALTO override applies.
 * @param max FWI value used to normalize the raw FWI into the [0,1] {@code fwiNorm} score
 *     component (values at or above this are clamped to 1).
 * @param calibrated {@code true} when these values are known-real for the method's own FWI
 *     scale (today, true only for PROXY_V1's already-shipped production constants);
 *     {@code false} when they are a provisional placeholder pending empirical calibration.
 * @param source human-readable provenance: where the values came from, and for
 *     uncalibrated entries, what still needs to happen before they can be trusted.
 */
public record FwiThresholds(
    double preventivo,
    double critico,
    double max,
    boolean calibrated,
    String source
) {
}
