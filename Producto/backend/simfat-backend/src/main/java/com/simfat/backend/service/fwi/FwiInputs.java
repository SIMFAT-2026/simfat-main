package com.simfat.backend.service.fwi;

/**
 * Today's noon-local-standard-time weather observation, as required by the Canadian Forest Fire
 * Weather Index System (Van Wagner &amp; Pickett 1985).
 *
 * <p>Input validation policy: {@code rhPct} is CLAMPED to {@code [0, 100]} rather than rejected,
 * because relative humidity cannot physically exceed 100% and a small sensor overshoot (Open-Meteo
 * and similar noon-weather APIs -- the documented eventual S1d input source -- are known to
 * occasionally report RH slightly above 100 near saturation) is a measurement artifact, not a data
 * error worth crashing on. Without this clamp, {@code rhPct > 100} makes the FFMC wetting branch
 * compute {@code Math.pow(negative, 1.7)}, which is {@code NaN} in Java, silently propagating
 * through FFMC-&gt;ISI-&gt;FWI-&gt;DSR. Similarly, {@code windKmh} and {@code precipMm} are clamped to
 * {@code >= 0}: negative values are not plausible measurement noise, but clamping (rather than
 * throwing) keeps this record's failure mode consistent for all three weather fields. {@code
 * month} is validated strictly (an {@link IllegalArgumentException}, not merely clamped) because
 * it selects a day-length table index and an out-of-range value is a caller programming error, not
 * measurement noise.
 *
 * @param tempC noon dry-bulb temperature, degrees Celsius
 * @param rhPct noon relative humidity, percent, clamped to {@code [0, 100]}
 * @param windKmh noon wind speed, km/h, clamped to {@code >= 0}
 * @param precipMm 24-hour accumulated precipitation ending at noon, mm, clamped to {@code >= 0}
 * @param month calendar month (1-12), used to select the DMC/DC day-length adjustment factors
 */
public record FwiInputs(double tempC, double rhPct, double windKmh, double precipMm, int month) {

    public FwiInputs {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be between 1 and 12, got " + month);
        }
        rhPct = Math.min(100.0, Math.max(0.0, rhPct));
        windKmh = Math.max(0.0, windKmh);
        precipMm = Math.max(0.0, precipMm);
    }
}
