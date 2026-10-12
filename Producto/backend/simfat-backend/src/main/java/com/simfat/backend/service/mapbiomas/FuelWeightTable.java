package com.simfat.backend.service.mapbiomas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads the committed, literature-derived MapBiomas fuel-weight table (design D3,
 * {@code fuel-weights-v1.json}) that {@link MapbiomasSusceptibilityServiceImpl} reads to
 * compute the {@code fuel} term of {@code S_mapbiomas}. Mirrors {@code FwiThresholdTable}'s
 * load-from-classpath idiom: fail loud and fast on a missing/malformed resource (there is no
 * safe default weight table to fall back to) and fail loud on an unknown class code at lookup
 * time rather than silently treating it as weight zero, which would be inventing a fact this
 * table does not contain.
 *
 * <p>The weights themselves are ordinal, evidence-strength-labeled literature placeholders
 * (engram {@code sdd/mapbiomas-integration/literature-review}, obs 729), explicitly
 * {@code calibrated=false} pending a forest-engineer review and empirical calibration
 * (decision Q9) -- same honesty pattern as {@code FwiThresholdTable}'s {@code VAN_WAGNER}
 * entries.
 *
 * <p>Class 27 (MapBiomas "not observed") is deliberately NOT a weighted class: it is listed in
 * {@code excludedClasses} and {@link #isExcluded} returns {@code true} for it, so callers must
 * skip it entirely rather than call {@link #weightFor} on it.
 *
 * <p><b>Numeric note (corrected):</b> in an un-renormalized weighted sum (see
 * {@code MapbiomasSusceptibilityServiceImpl#computeFuelIndex}), skipping class 27's share
 * entirely is numerically IDENTICAL to the resulting fuel index as if class 27 had an explicit
 * {@code 0.0} entry in {@code classWeights} -- it does NOT preserve a distinction between "we
 * do not know what is there" and "there is nothing flammable there" in the returned score. The
 * real reasons this table tracks class 27 as excluded rather than as a literal {@code 0.0}
 * weight entry are: (1) self-documentation -- a {@code 0.0} weight would read as an
 * evidence-based "zero flammability" literature judgment, which is not true for an unclassified
 * pixel; and (2) safety -- it lets {@link #weightFor} keep failing loudly on a genuinely unknown
 * class code, instead of a maintainer mistaking a missing entry for 27 as a bug and adding a
 * fabricated weight for it. Consumers that need the "how much of this comuna is unobserved"
 * signal must read it separately (see {@code MapbiomasSusceptibility#unobservedShare}), not
 * infer it from the fuel index.
 */
@Component
public class FuelWeightTable {

    private static final String DEFAULT_RESOURCE_PATH = "mapbiomas/fuel-weights-v1.json";

    private final Map<String, Double> classWeights;
    private final List<String> excludedClasses;

    public FuelWeightTable(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_RESOURCE_PATH);
    }

    // Test seam: lets tests point the loader at a resource path that does not exist, to prove
    // the "clear exception, not a silent fallback" contract on load failure. Same idiom as
    // FwiThresholdTable's test-only constructor.
    FuelWeightTable(ObjectMapper objectMapper, String resourcePath) {
        WeightTableResource resource = loadFromResource(objectMapper, resourcePath);
        this.classWeights = resource.classWeights();
        this.excludedClasses = resource.excludedClasses() == null
            ? Collections.emptyList()
            : resource.excludedClasses();
    }

    /**
     * @param classCode a MapBiomas land-cover class code, e.g. {@code "9"} (silviculture).
     * @throws IllegalArgumentException if {@code classCode} has no entry in the table AND is
     *     not an excluded class -- callers must not silently treat an unknown code as weight
     *     zero.
     */
    public double weightFor(String classCode) {
        Double weight = classWeights.get(classCode);
        if (weight == null) {
            throw new IllegalArgumentException(
                "Unknown MapBiomas land-cover class code \"" + classCode + "\" has no fuel "
                    + "weight entry in fuel-weights-v1.json. Known classes: " + classWeights.keySet()
                    + ". Excluded (never contribute to fuel): " + excludedClasses
            );
        }
        return weight;
    }

    /** True for class codes (e.g. {@code "27"}, unobserved) excluded from the fuel sum. */
    public boolean isExcluded(String classCode) {
        return excludedClasses.contains(classCode);
    }

    private static WeightTableResource loadFromResource(ObjectMapper objectMapper, String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream()) {
            return objectMapper.readValue(is, WeightTableResource.class);
        } catch (IOException ex) {
            // Fail fast and loud, same rationale as FwiThresholdTable: a missing/malformed
            // weight table means fuel can never be computed, and there is no safe default.
            throw new IllegalStateException(
                "Failed to load MapBiomas fuel-weight table from classpath resource \"" + path + "\"", ex
            );
        }
    }

    // Deserialization target only -- ignores tableId/provenance, this service does not need
    // them at runtime (they exist in the JSON for human/report traceability).
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WeightTableResource(Map<String, Double> classWeights, List<String> excludedClasses) {}
}
