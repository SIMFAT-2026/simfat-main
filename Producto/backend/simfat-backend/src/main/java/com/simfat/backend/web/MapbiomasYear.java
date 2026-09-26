package com.simfat.backend.web;

import com.simfat.backend.exception.BadRequestException;
import java.util.regex.Pattern;

/**
 * Bounded {@code year} parameter for the MapBiomas LANDCOVER/BURNED layers and the public
 * detail route (design D5). Ranges live in this ONE class, beside {@link PublicQueryWindow}:
 *
 * <ul>
 *   <li>LANDCOVER: 1999-2024 (inclusive)</li>
 *   <li>BURNED: 2013-2025 (inclusive)</li>
 * </ul>
 *
 * <p>Parsing is strict: the raw value must match {@code ^[0-9]{1,4}$} exactly (no leading/
 * trailing whitespace, no sign, no decimal point, no scientific notation, no lenient coercion),
 * so a request can never trigger an unhandled parse exception -- malformed or out-of-range input
 * always surfaces as a {@link BadRequestException} (same style as {@link PublicQueryWindow}),
 * never a 500.
 *
 * <p>{@code null} (the parameter genuinely absent from the request) resolves to the latest
 * available year for the indicator. An EMPTY string (the parameter present but blank, e.g.
 * {@code ?year=}) is NOT treated as omitted -- it is malformed and rejected, exactly like any
 * other value that fails the strict pattern.
 */
public final class MapbiomasYear {

    public static final int LANDCOVER_MIN_YEAR = 1999;
    public static final int LANDCOVER_MAX_YEAR = 2024;
    public static final int BURNED_MIN_YEAR = 2013;
    public static final int BURNED_MAX_YEAR = 2025;

    // Anchored on both ends: at most 4 digits rules out overflow, decimals, signs, exponents
    // and embedded whitespace all in one check, before any call to Integer.parseInt.
    private static final Pattern STRICT_YEAR = Pattern.compile("^[0-9]{1,4}$");

    /** The two MapBiomas layers that carry a year selector (design D5/D6). */
    public enum Indicator {
        LANDCOVER,
        BURNED
    }

    private final Indicator indicator;
    private final int year;

    private MapbiomasYear(Indicator indicator, int year) {
        this.indicator = indicator;
        this.year = year;
    }

    /**
     * Resolves and validates a raw {@code year} query value for the given indicator.
     *
     * @param indicator the MapBiomas indicator the year is validated against
     * @param rawYear the raw request parameter; {@code null} means the parameter was omitted
     * @throws BadRequestException if {@code rawYear} is present but malformed or out of range
     */
    public static MapbiomasYear resolve(Indicator indicator, String rawYear) {
        int min = minYear(indicator);
        int max = maxYear(indicator);

        if (rawYear == null) {
            return new MapbiomasYear(indicator, max);
        }

        if (!STRICT_YEAR.matcher(rawYear).matches()) {
            throw new BadRequestException(
                "El parametro 'year' es invalido para " + indicator
                    + " (se espera un entero entre " + min + " y " + max + ")");
        }

        int parsed;
        try {
            parsed = Integer.parseInt(rawYear);
        } catch (NumberFormatException ex) {
            // Unreachable in practice (the pattern already bounds the value to <= 4 digits,
            // well within int range), kept as a defensive fail-loud-as-400 guard.
            throw new BadRequestException(
                "El parametro 'year' es invalido para " + indicator
                    + " (se espera un entero entre " + min + " y " + max + ")");
        }

        if (parsed < min || parsed > max) {
            throw new BadRequestException(
                "El parametro 'year' esta fuera de rango para " + indicator
                    + ": " + parsed + " (rango permitido " + min + "-" + max + ")");
        }

        return new MapbiomasYear(indicator, parsed);
    }

    public static int minYear(Indicator indicator) {
        return indicator == Indicator.LANDCOVER ? LANDCOVER_MIN_YEAR : BURNED_MIN_YEAR;
    }

    public static int maxYear(Indicator indicator) {
        return indicator == Indicator.LANDCOVER ? LANDCOVER_MAX_YEAR : BURNED_MAX_YEAR;
    }

    public Indicator indicator() {
        return indicator;
    }

    public int year() {
        return year;
    }

    public int minYear() {
        return minYear(indicator);
    }

    public int maxYear() {
        return maxYear(indicator);
    }
}
