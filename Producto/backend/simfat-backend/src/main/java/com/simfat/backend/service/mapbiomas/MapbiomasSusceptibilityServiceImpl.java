package com.simfat.backend.service.mapbiomas;

import com.simfat.backend.model.ComunaMapbiomasStats;
import com.simfat.backend.repository.ComunaMapbiomasStatsRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * S2a1: {@link MapbiomasSusceptibilityService}, reading a single comuna's
 * {@code comuna_mapbiomas_stats} (S1b2) row and computing {@code S_mapbiomas} per design D3.
 *
 * <p><b>Bulk-read deferral.</b> {@code ComunaMapbiomasStatsRepository#findByDataVersion}
 * exists specifically for NFR-7's "one bulk read per scoring run" need, but nothing in this
 * slice calls it: {@link #forComuna} is a single-comuna lookup pinned to
 * {@code mapbiomas.stats.data-version}. Building a per-run bulk cache here would be premature
 * -- there is no scoring loop yet to feed it (that is S2a2's job, wiring this service into
 * {@code ComunaRiskServiceImpl.recomputeAllComunas}). Deferred to S2a2, documented rather than
 * silently skipped.
 *
 * <p><b>Percentile-rank blocker RESOLVED (S1b3).</b> Design D3 (engram
 * {@code sdd/mapbiomas-integration/design}) normalizes burned area via
 * {@code fire.burnedFractionPct}, an empirical percentile rank across the 86 comunas, frozen per
 * {@code dataVersion} (ties take the mean rank, exact zeros pinned to 0.0). This field now exists
 * on {@link ComunaMapbiomasStats.Fire} and is populated for every {@code available=true} comuna
 * in the committed seed (Python: {@code mb_pipeline.build_stats.add_burned_fraction_pct}).
 * {@link #computeBurnedNormResult} uses it whenever it is non-null. The S2a2 wiring blocker
 * described in the S2a1 apply-progress (engram obs 774) is therefore resolved: a future {@code
 * wM > 0} rollout no longer silently ships the raw-fraction proxy. The proxy is kept ONLY as a
 * fallback for a comuna whose {@code fire.available=true} but {@code burnedFractionPct} is
 * {@code null} (e.g. a stats document predating S1b3, or a future regression in the pipeline);
 * when that fallback fires, {@link MapbiomasSusceptibility#qualityFlag} carries {@link
 * MapbiomasSusceptibility#MAPBIOMAS_BURNED_NORM_RAW_FALLBACK} so the degradation is visible to
 * callers instead of silent.
 */
@Service
public class MapbiomasSusceptibilityServiceImpl implements MapbiomasSusceptibilityService {

    // design D3: S_mapbiomas = 0.65*fuel + 0.35*history (full blend, both components available)
    static final double FUEL_WEIGHT = 0.65;
    static final double HISTORY_WEIGHT = 0.35;

    // design D3: history = clamp(0.60*burnedNorm + 0.40*recurrenceNorm) * recency
    static final double BURNED_WEIGHT = 0.60;
    static final double RECURRENCE_WEIGHT = 0.40;
    static final double FREQUENCY_CAP = 5.0;

    // design D1: sharesByClass values are FRACTIONS (0-1) summing to ~1.0 per comuna-year, not
    // basis points (0-10000). Verified directly against the committed pipeline
    // (Producto/analytics/mapbiomas/src/mb_pipeline/build_stats.py): land_cover_section()
    // currently stores to_basis_points()'s bp integers straight into sharesByClass with no
    // conversion back to a fraction, which contradicts D1's stated shape for this field (only
    // the newer sharesByYear.classes field is documented as bp). This tolerance guards against
    // that live unit mismatch (or any future drift) by failing loudly instead of silently
    // computing a saturated, meaningless fuel index.
    static final double SHARE_SUM_TOLERANCE = 0.01;

    // MapBiomas Land Cover Col 2 class code for "not observed" -- see FuelWeightTable's class
    // Javadoc for why it is excluded from the fuel sum rather than given a literal 0.0 weight.
    private static final String UNOBSERVED_CLASS_CODE = "27";

    private final ComunaMapbiomasStatsRepository statsRepository;
    private final FuelWeightTable fuelWeightTable;

    // Config property, not a code literal, so a future collection refresh (a new dataVersion)
    // is a config change, not a code change (design D1 "Collection refresh" open question).
    @Value("${mapbiomas.stats.data-version:fuego-col1@2017-partial}")
    private String dataVersion;

    // design D3 recency multiplier: recency = 1 + R_MAX * exp(-yearsSinceLastFire / TAU).
    // R_MAX defaults to 0.0 (disabled/neutral, recency == 1.0) -- the literature conflicts on
    // the sign (Davim 2023 negative feedback vs Fernandez-Guisuraga & Calvo 2023 higher reburn
    // severity, engram obs 729); shipping a sign we cannot justify in Chile would be inventing
    // a fact. Config-injectable so a later slice can enable it once the empirical selectivity
    // calibration and forest engineers decide (open question in design-part2).
    @Value("${mapbiomas.history.recency.r-max:0.0}")
    private double recencyRMax;

    @Value("${mapbiomas.history.recency.tau:8.0}")
    private double recencyTau = 8.0;

    public MapbiomasSusceptibilityServiceImpl(
        ComunaMapbiomasStatsRepository statsRepository,
        FuelWeightTable fuelWeightTable
    ) {
        this.statsRepository = statsRepository;
        this.fuelWeightTable = fuelWeightTable;
    }

    // Test seam: @Value fields are only set by Spring; tests construct this component
    // directly (same idiom as MapbiomasSeedLoader#setDataVersion).
    void setDataVersion(String dataVersion) {
        this.dataVersion = dataVersion;
    }

    void setRecencyRMax(double recencyRMax) {
        this.recencyRMax = recencyRMax;
    }

    void setRecencyTau(double recencyTau) {
        this.recencyTau = recencyTau;
    }

    @Override
    public Optional<MapbiomasSusceptibility> forComuna(String comunaId) {
        Optional<ComunaMapbiomasStats> statsOpt =
            statsRepository.findByComunaIdAndDataVersion(comunaId, dataVersion);
        return statsOpt.map(this::computeFrom);
    }

    @Override
    public Map<String, MapbiomasSusceptibility> forComunas(Collection<String> comunaIds) {
        // Single bulk read (NFR-7) regardless of |comunaIds|, instead of one
        // findByComunaIdAndDataVersion call per comuna -- see the class Javadoc's "Bulk-read
        // deferral" note, which explicitly assigns this to S2a2.
        Set<String> requested = new HashSet<>(comunaIds);
        Map<String, MapbiomasSusceptibility> result = new HashMap<>();
        for (ComunaMapbiomasStats stats : statsRepository.findByDataVersion(dataVersion)) {
            if (requested.contains(stats.getComunaId())) {
                result.put(stats.getComunaId(), computeFrom(stats));
            }
        }
        return result;
    }

    private MapbiomasSusceptibility computeFrom(ComunaMapbiomasStats stats) {
        boolean fireAvailable = isFireAvailable(stats.getFire());
        boolean landCoverAvailable = isLandCoverAvailable(stats.getLandCover());

        Double fuelIndex = landCoverAvailable
            ? computeFuelIndex(stats.getLandCover().getSharesByClass(), fuelWeightTable)
            : null;
        HistoryResult historyResult = fireAvailable
            ? computeHistoryIndexResult(stats.getFire(), recencyRMax, recencyTau)
            : null;
        Double historyIndex = historyResult != null ? historyResult.historyIndex() : null;
        // Informational only (see MapbiomasSusceptibility#unobservedShare) -- does NOT feed
        // fuelIndex or score. null when landCover itself is unavailable, distinct from 0.0
        // (class 27 present but with zero share).
        Double unobservedShare = landCoverAvailable
            ? stats.getLandCover().getSharesByClass().getOrDefault(UNOBSERVED_CLASS_CODE, 0.0)
            : null;

        double score;
        String baseFlag;
        if (landCoverAvailable && fireAvailable) {
            score = clamp01(FUEL_WEIGHT * fuelIndex + HISTORY_WEIGHT * historyIndex);
            baseFlag = null;
        } else if (fireAvailable) {
            // landCover missing (decision Q26, today's reality for all 86 comunas): the score
            // is the history component ALONE, never 0.65*0 + 0.35*history -- that would
            // silently penalize the score by the fuel weight for having no fuel data, which is
            // worse than not having a fuel signal at all.
            score = historyIndex;
            baseFlag = MapbiomasSusceptibility.MAPBIOMAS_FUEL_UNAVAILABLE;
        } else if (landCoverAvailable) {
            // Symmetric case (design D2's Biobio-coverage note): fire.available=false ->
            // fuel-only, never 0.65*fuel + 0.35*0.
            score = fuelIndex;
            baseFlag = MapbiomasSusceptibility.MAPBIOMAS_FIRE_UNAVAILABLE;
        } else {
            // A stats document exists (we did not return Optional.empty() above) but neither
            // component is usable. There is no signal at all to report. score=0.0 is a safe
            // placeholder, NOT a claim of zero susceptibility -- S2a2 must treat this
            // qualityFlag as equivalent to "no data" when wiring the blend, not multiply a
            // literal 0.0 in.
            score = 0.0;
            baseFlag = MapbiomasSusceptibility.MAPBIOMAS_UNAVAILABLE;
        }

        // Independent of the base flag above: fire.burnedFractionPct (design D3's intended
        // burnedNorm) was absent even though fire was available, so computeHistoryIndexResult
        // fell back to the raw-fraction proxy. Combine, don't overwrite -- both conditions can
        // hold at once (e.g. landCover missing AND pct missing).
        boolean usedRawBurnedNormFallback =
            historyResult != null && historyResult.usedRawBurnedNormFallback();
        String qualityFlag = combineFlags(
            baseFlag,
            usedRawBurnedNormFallback ? MapbiomasSusceptibility.MAPBIOMAS_BURNED_NORM_RAW_FALLBACK : null
        );

        return new MapbiomasSusceptibility(
            score, fuelIndex, historyIndex, stats.getDataVersion(), qualityFlag, unobservedShare
        );
    }

    private static String combineFlags(String... flags) {
        StringBuilder combined = null;
        for (String flag : flags) {
            if (flag == null) {
                continue;
            }
            if (combined == null) {
                combined = new StringBuilder(flag);
            } else {
                combined.append(',').append(flag);
            }
        }
        return combined == null ? null : combined.toString();
    }

    private static boolean isFireAvailable(ComunaMapbiomasStats.Fire fire) {
        return fire != null && Boolean.TRUE.equals(fire.getAvailable());
    }

    private static boolean isLandCoverAvailable(ComunaMapbiomasStats.LandCover landCover) {
        return landCover != null && landCover.getSharesByClass() != null;
    }

    /**
     * {@code fuel = SUM_c share_c * weight_c} (design D3), skipping class 27 (unobserved,
     * {@link FuelWeightTable#isExcluded}) entirely rather than looking it up in the weight
     * table. Package-private and static so it is directly unit-testable without Mongo or
     * Spring.
     *
     * <p><b>Numeric note (corrected):</b> dropping class 27's share from this un-renormalized
     * weighted sum is numerically IDENTICAL to the resulting {@code fuel} value as if class 27
     * had an explicit {@code 0.0} entry in the weight table -- it is not a different score
     * outcome. See {@link FuelWeightTable}'s class Javadoc for why it is tracked as "excluded"
     * rather than as a literal zero-weight table entry.
     *
     * <p><b>Unit validation.</b> {@code sharesByClass} values must be FRACTIONS (0-1) summing
     * to ~1.0 for a comuna-year (design D1), never basis points (0-10000). This is checked
     * before the weighted sum so a unit mismatch fails loudly with the actual sum, rather than
     * silently producing a saturated {@code fuel} close to 1.0.
     *
     * @throws IllegalArgumentException if the shares do not sum to ~1.0 within
     *     {@link #SHARE_SUM_TOLERANCE}, or if a class code in {@code sharesByClass} has no
     *     entry in the weight table and is not excluded -- fails loudly rather than silently
     *     assuming weight zero for an unknown class (that would be inventing a fact).
     */
    static double computeFuelIndex(Map<String, Double> sharesByClass, FuelWeightTable weights) {
        double sum = 0.0;
        for (Double value : sharesByClass.values()) {
            sum += value == null ? 0.0 : value;
        }
        if (Math.abs(sum - 1.0) > SHARE_SUM_TOLERANCE) {
            throw new IllegalArgumentException(
                "MapBiomas land-cover shares must sum to ~1.0 (fractions), but got " + sum
                    + ". This looks like a basis-points/fraction unit mismatch (e.g. values in "
                    + "0-10000 instead of 0-1) -- check the seed pipeline's sharesByClass unit."
            );
        }
        double fuel = 0.0;
        for (Map.Entry<String, Double> entry : sharesByClass.entrySet()) {
            String classCode = entry.getKey();
            if (weights.isExcluded(classCode)) {
                continue;
            }
            double share = entry.getValue() == null ? 0.0 : entry.getValue();
            fuel += share * weights.weightFor(classCode);
        }
        return clamp01(fuel);
    }

    /**
     * {@code history} plus whether it fell back to the raw-fraction {@code burnedNorm} proxy
     * (see {@link MapbiomasSusceptibility#MAPBIOMAS_BURNED_NORM_RAW_FALLBACK}), returned together
     * so {@link #forComuna} can surface the fallback as a quality flag without recomputing it.
     */
    record HistoryResult(double historyIndex, boolean usedRawBurnedNormFallback) {}

    /** {@code burnedNorm} plus whether it is the raw-fraction fallback (design D3, S1b3). */
    private record BurnedNormResult(double value, boolean usedRawFallback) {}

    /** {@code history = clamp(0.60*burnedNorm + 0.40*recurrenceNorm) * recency} (design D3). */
    static HistoryResult computeHistoryIndexResult(
        ComunaMapbiomasStats.Fire fire, double recencyRMax, double recencyTau
    ) {
        BurnedNormResult burnedNormResult = computeBurnedNormResult(fire);
        double recurrenceNorm = computeRecurrenceNorm(fire.getFrequencyMean());
        double recency = computeRecency(fire.getYearsSinceLastFire(), recencyRMax, recencyTau);
        double raw = BURNED_WEIGHT * burnedNormResult.value() + RECURRENCE_WEIGHT * recurrenceNorm;
        return new HistoryResult(clamp01(raw * recency), burnedNormResult.usedRawFallback());
    }

    /**
     * {@code burnedNorm} (design D3): {@code fire.burnedFractionPct} (the empirical percentile
     * rank across all comunas, S1b3) when present, otherwise the raw most-recent-year {@code
     * burnedFractionByYear} fraction as a fallback (flagged by the returned {@code
     * usedRawFallback}, see {@link MapbiomasSusceptibility#MAPBIOMAS_BURNED_NORM_RAW_FALLBACK}).
     */
    private static BurnedNormResult computeBurnedNormResult(ComunaMapbiomasStats.Fire fire) {
        Double pct = fire.getBurnedFractionPct();
        if (pct != null) {
            return new BurnedNormResult(clamp01(pct), false);
        }
        return new BurnedNormResult(computeBurnedNorm(fire.getBurnedFractionByYear()), true);
    }

    /**
     * Raw-fraction {@code burnedNorm} FALLBACK ONLY (design D3's intended {@code burnedNorm} is
     * {@code fire.burnedFractionPct}, see {@link #computeBurnedNormResult}): the most recent
     * year's raw {@code burnedFractionByYear} value, used when {@code burnedFractionPct} is
     * unavailable. Kept as its own package-private static method (unchanged signature) so it
     * stays directly unit-testable without constructing a whole {@code Fire} object.
     */
    static double computeBurnedNorm(Map<String, Double> burnedFractionByYear) {
        if (burnedFractionByYear == null || burnedFractionByYear.isEmpty()) {
            return 0.0;
        }
        String mostRecentYear = burnedFractionByYear.keySet().stream()
            .max(Comparator.comparingInt(Integer::parseInt))
            .orElseThrow();
        Double fraction = burnedFractionByYear.get(mostRecentYear);
        return fraction == null ? 0.0 : clamp01(fraction);
    }

    static double computeRecurrenceNorm(Double frequencyMean) {
        if (frequencyMean == null) {
            return 0.0;
        }
        return clamp01(Math.min(frequencyMean, FREQUENCY_CAP) / FREQUENCY_CAP);
    }

    static double computeRecency(Integer yearsSinceLastFire, double recencyRMax, double recencyTau) {
        if (yearsSinceLastFire == null || recencyRMax == 0.0) {
            // R_MAX=0 (config default) disables the multiplier entirely (neutral, *1.0).
            return 1.0;
        }
        return 1.0 + recencyRMax * Math.exp(-yearsSinceLastFire / recencyTau);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
