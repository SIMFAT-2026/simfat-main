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
 * <p>{@code null} (the parameter genuinely absent from the request) resolves to a default year
 * for the indicator -- see {@link #resolve(Indicator, String, int)}. An EMPTY string (the
 * parameter present but blank, e.g. {@code ?year=}) is NOT treated as omitted -- it is malformed
 * and rejected, exactly like any other value that fails the strict pattern.
 *
 * <p><b>Decision Q28</b> (engram {@code sdd/mapbiomas-integration/decisions-q28}): for BURNED,
 * the omitted-{@code year} default is no longer the fixed {@link #BURNED_MAX_YEAR} constant --
 * it is the LATEST year that actually has burned data in the loaded {@code dataVersion},
 * computed by the caller ({@code TerritoryController}, via {@code MapbiomasBurnedDefaultYearResolver})
 * over the whole dataVersion (all regions, so switching regions never shifts the default) and
 * passed in here. This class stays pure and dependency-free: it never reads stats itself, it
 * only validates that the caller-provided default still lies within the indicator's own range.
 * LANDCOVER mirrors BURNED (slice S3-0): its omitted-{@code year} default is data-derived by
 * {@code MapbiomasLandCoverDefaultYearResolver} -- the latest year with land-cover data (2024 with
 * the current seed); {@link #LANDCOVER_MAX_YEAR} is only the resolver's fallback when no comuna
 * qualifies and the upper bound of the accepted range.
 * <b>Tradeoff accepted</b>: a URL saved without {@code ?year=} will show a different BURNED or
 * LANDCOVER year once more years are processed into the seed -- the frontend should always send an
 * explicit {@code year} once one has been shown or selected (design D6: legend/tooltip always
 * print the resolved year).
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
     * Resolves and validates a raw {@code year} query value for the given indicator, defaulting
     * to {@link #maxYear(Indicator)} when {@code rawYear} is omitted. Equivalent to
     * {@code resolve(indicator, rawYear, maxYear(indicator))} -- a convenience overload for
     * callers with no data-derived default. Both LANDCOVER and BURNED callers in
     * {@code TerritoryController} use the three-argument overload with a data-derived default
     * (see {@code MapbiomasLandCoverDefaultYearResolver} and {@code MapbiomasBurnedDefaultYearResolver}).
     *
     * @param indicator the MapBiomas indicator the year is validated against
     * @param rawYear the raw request parameter; {@code null} means the parameter was omitted
     * @throws BadRequestException if {@code rawYear} is present but malformed or out of range
     */
    public static MapbiomasYear resolve(Indicator indicator, String rawYear) {
        return resolve(indicator, rawYear, maxYear(indicator));
    }

    /**
     * Resolves and validates a raw {@code year} query value for the given indicator, defaulting
     * to {@code defaultYear} when {@code rawYear} is omitted (Decision Q28 -- the caller computes
     * a data-derived default for both LANDCOVER and BURNED).
     *
     * @param indicator the MapBiomas indicator the year is validated against
     * @param rawYear the raw request parameter; {@code null} means the parameter was omitted
     * @param defaultYear the year to use when {@code rawYear} is omitted; MUST lie within
     *     {@code [minYear(indicator), maxYear(indicator)]}
     * @throws BadRequestException if {@code rawYear} is present but malformed or out of range
     * @throws IllegalArgumentException if {@code defaultYear} itself lies outside the
     *     indicator's own range -- a caller/programming defect, never a user-facing 400
     */
    public static MapbiomasYear resolve(Indicator indicator, String rawYear, int defaultYear) {
        int min = minYear(indicator);
        int max = maxYear(indicator);

        if (defaultYear < min || defaultYear > max) {
            throw new IllegalArgumentException(
                "defaultYear " + defaultYear + " is outside the " + indicator
                    + " range (" + min + "-" + max + ")");
        }

        if (rawYear == null) {
            return new MapbiomasYear(indicator, defaultYear);
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
