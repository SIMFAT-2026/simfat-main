package com.simfat.backend.service.fwi;

/**
 * Legacy Open-Meteo-derived proxy FWI approximation, moved verbatim (no logic change) from
 * {@code OpenWeatherFwiServiceImpl.computeProxyFwi} during S1d2 so it can be selected
 * explicitly via the {@code territory.fwi.method=PROXY_V1} config flag — the default, and
 * today's unchanged production behavior — alongside the real Van Wagner chain ({@code
 * territory.fwi.method=VAN_WAGNER}, see {@link CanadianFwiCalculator}).
 *
 * <p>Proxy FWI on a 0-60 scale (similar to CFWI: &lt;15 low, 15-30 moderate, 30-45 high,
 * &gt;45 extreme). Documented approximation for MVP based on the variables available from
 * Open-Meteo's free tier (no {@code fire_danger_index} field exists there). Sources: CFWI
 * meteorological relationships (temperature-FFMC, humidity-FFMC, wind-ISI).
 */
public final class LegacyProxyFwiCalculator {

    private LegacyProxyFwiCalculator() {
    }

    public static double computeProxyFwi(double tempMax, double rhMin, double windMaxKmh, double precipMm) {
        // Factor de secado: temperatura alta eleva peligro
        double tempFactor = Math.max(0, Math.min(1.0, tempMax / 40.0));

        // Factor de sequedad: humedad minima del dia (peor caso)
        double drynessFactor = Math.max(0, (100.0 - rhMin) / 100.0);

        // Factor de viento: velocidad maxima del dia
        double windFactor = Math.max(0, Math.min(1.0, windMaxKmh / 60.0));

        // Amortiguacion por precipitacion: 3mm+ reduce significativamente el riesgo
        double rainFactor = Math.max(0.0, 1.0 - precipMm / 3.0);

        // Compuesto: dryness domina (40%), temperatura (30%), viento (30%)
        double raw = 60.0 * (0.40 * drynessFactor + 0.30 * tempFactor + 0.30 * windFactor) * rainFactor;
        return Math.max(0, Math.min(60.0, raw));
    }
}
