package com.simfat.backend.service.mapbiomas;

import java.util.Arrays;

/**
 * Result of {@link MapbiomasSusceptibilityService#forComuna}, design D3.
 *
 * @param score {@code S_mapbiomas} in {@code [0,1]}. See {@code qualityFlag} for whether this
 *     is the full 0.65/0.35 fuel+history blend or a degraded, single-component signal.
 * @param fuelIndex {@code fuel} in {@code [0,1]}, or {@code null} when {@code landCover} is
 *     unavailable for this comuna -- {@code null} means "genuinely unknown", never an implicit
 *     zero (a zero fuel index would claim "no flammable vegetation", which is a different,
 *     unsupported statement).
 * @param historyIndex {@code history} in {@code [0,1]}, or {@code null} when fire data is
 *     unavailable ({@code fire.available=false} or missing), for the same reason.
 * @param dataVersion the {@code comuna_mapbiomas_stats} {@code dataVersion} this result was
 *     computed from, so callers can attribute/audit it.
 * @param qualityFlag {@code null} when both components are available AND {@code
 *     fire.burnedFractionPct} was used (the full D3 formula, no degradation); otherwise a
 *     comma-separated list of one or more of {@link #MAPBIOMAS_FUEL_UNAVAILABLE}, {@link
 *     #MAPBIOMAS_FIRE_UNAVAILABLE}, {@link #MAPBIOMAS_UNAVAILABLE}, {@link
 *     #MAPBIOMAS_BURNED_NORM_RAW_FALLBACK} -- these are INDEPENDENT conditions (e.g. landCover
 *     can be unavailable at the same time {@code burnedFractionPct} is absent), so more than one
 *     may apply at once. This is the least invasive extension of a single {@code String} field:
 *     a dedicated flags collection would touch every existing caller for a condition that should
 *     be rare once every comuna's seed carries {@code burnedFractionPct} (S1b3 regenerated the
 *     committed seed with it for every {@code available=true} comuna) -- the fallback flag exists
 *     to make a REGRESSION of that (a future seed missing the field again, or a stats document
 *     predating S1b3) visible instead of silently reverting to the raw-fraction proxy. Use {@link
 *     #hasFlag(String)} to read this field -- never a hand-rolled comma split or a substring
 *     {@code contains} check, since e.g. {@link #MAPBIOMAS_UNAVAILABLE} is a substring of {@link
 *     #MAPBIOMAS_FUEL_UNAVAILABLE}.
 * @param unobservedShare class 27's ("not observed") share of {@code sharesByClass} when
 *     {@code landCover} is available (0.0 when class 27 has no entry for this comuna), or
 *     {@code null} when {@code landCover} itself is unavailable. This is informational only --
 *     it does NOT change {@code fuelIndex} (see {@link FuelWeightTable}'s class Javadoc) --
 *     surfaced so a future consumer (e.g. a UI or the S2a2 blend) can flag/discount a comuna
 *     whose fuel score is diluted by a large unclassified/cloud-covered area, instead of that
 *     information being silently lost.
 */
public record MapbiomasSusceptibility(
    double score,
    Double fuelIndex,
    Double historyIndex,
    String dataVersion,
    String qualityFlag,
    Double unobservedShare
) {

    /**
     * {@code landCover} is null for this comuna's stats document (decision Q26 -- the reality
     * for all 86 comunas today, since only S1b2's fire-only 2017 seed is loaded). {@code score}
     * is the {@code history} component ALONE, not a fuel-penalized blend.
     */
    public static final String MAPBIOMAS_FUEL_UNAVAILABLE = "MAPBIOMAS_FUEL_UNAVAILABLE";

    /**
     * {@code fire.available=false} (or {@code fire} missing) for this comuna, symmetric to
     * {@link #MAPBIOMAS_FUEL_UNAVAILABLE} (design D2 Biobio-coverage note). {@code score} is
     * the {@code fuel} component ALONE.
     */
    public static final String MAPBIOMAS_FIRE_UNAVAILABLE = "MAPBIOMAS_FIRE_UNAVAILABLE";

    /**
     * Neither {@code landCover} nor an available {@code fire} exists for this comuna, even
     * though a {@code comuna_mapbiomas_stats} document does (distinct from
     * {@link MapbiomasSusceptibilityService#forComuna} returning {@code Optional.empty()},
     * which strictly means "no document at all"). {@code score} is {@code 0.0} as a safe
     * placeholder, NOT a claim of "no susceptibility" -- a future consumer wiring this into a
     * score blend MUST treat this flag the same as "absent data", not blend in the literal
     * {@code 0.0}.
     */
    public static final String MAPBIOMAS_UNAVAILABLE = "MAPBIOMAS_UNAVAILABLE";

    /**
     * {@code fire.burnedFractionPct} (design D3's intended {@code burnedNorm} -- the empirical
     * percentile rank of the burned fraction across all comunas, S1b3) was {@code null} for this
     * comuna even though {@code fire.available=true}, so {@link
     * MapbiomasSusceptibilityServiceImpl#computeBurnedNorm} fell back to the raw most-recent-year
     * {@code burnedFractionByYear} fraction instead. Independent of {@link
     * #MAPBIOMAS_FUEL_UNAVAILABLE}/{@link #MAPBIOMAS_FIRE_UNAVAILABLE} -- may appear alongside
     * either in a comma-separated {@code qualityFlag}.
     */
    public static final String MAPBIOMAS_BURNED_NORM_RAW_FALLBACK = "MAPBIOMAS_BURNED_NORM_RAW_FALLBACK";

    /**
     * Returns whether {@code flag} is one of the tokens in the comma-joined {@link
     * #qualityFlag}. Matches an exact token after splitting on {@code ","}, never a substring
     * (a substring match would wrongly consider {@link #MAPBIOMAS_UNAVAILABLE} present whenever
     * {@link #MAPBIOMAS_FUEL_UNAVAILABLE} is).
     *
     * @param flag the flag constant to look for; must be non-null and non-blank.
     * @return {@code false} when {@link #qualityFlag} is {@code null}; otherwise whether {@code
     *     flag} matches one of its comma-separated tokens exactly.
     * @throws IllegalArgumentException if {@code flag} is {@code null} or blank.
     */
    public boolean hasFlag(String flag) {
        if (flag == null || flag.isBlank()) {
            throw new IllegalArgumentException("flag must not be null or blank");
        }
        if (qualityFlag == null) {
            return false;
        }
        return Arrays.stream(qualityFlag.split(",")).anyMatch(flag::equals);
    }
}
