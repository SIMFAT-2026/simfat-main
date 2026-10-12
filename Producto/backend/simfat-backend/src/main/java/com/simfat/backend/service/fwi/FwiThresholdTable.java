package com.simfat.backend.service.fwi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Per-{@code territory.fwi.method} alert threshold table (S1e2b, task 1e.2).
 *
 * <p><b>Scope note (read before touching {@code VAN_WAGNER} values):</b> the design originally
 * called for this table's {@code VAN_WAGNER} thresholds to be derived by quantile matching --
 * finding the FWI values that sit at the same percentiles PROXY_V1's own thresholds (20 and 45)
 * occupy in the proxy's historical distribution. That calibration requires a real, empirical
 * Van Wagner FWI history, which does not exist yet: S1e2a's {@code FwiSpinUpRunner} has never
 * been run against production data. Fabricating threshold numbers without that data would
 * violate this project's "never invent numbers we don't have evidence for" discipline (see
 * design decisions D3/D4, {@code sdd/mapbiomas-integration/design} and {@code design-part2}).
 *
 * <p>So this class currently does ONLY ONE thing: load a committed, per-method threshold table
 * from {@code fwi-thresholds-v1.json}, with {@code PROXY_V1} holding the real production
 * defaults and {@code VAN_WAGNER} holding the SAME numeric values marked {@code calibrated=false}
 * as an honest, safe placeholder. It performs NO percentile/quantile computation. Before
 * {@code VAN_WAGNER} can be marked {@code calibrated=true}, someone must: (1) run a real S1e2a
 * spin-up against production data so {@code comuna_fwi_state} holds a genuine Van Wagner FWI
 * history, (2) run an offline analysis computing the quantile-matched threshold values from that
 * history, and (3) update {@code fwi-thresholds-v1.json} with the computed values.
 *
 * <p>No consumer is wired to this table yet -- {@code ComunaRiskServiceImpl} still uses its own
 * hardcoded {@code FWI_PREVENTIVO}/{@code FWI_CRITICO}/{@code FWI_MAX} constants. Wiring this
 * table into the score/override logic is deliberately out of scope for this slice (a later unit,
 * e.g. S2a's score blend work).
 */
@Component
public class FwiThresholdTable {

    private static final String DEFAULT_RESOURCE_PATH = "fwi/fwi-thresholds-v1.json";

    private final Map<String, FwiThresholds> thresholdsByMethod;

    public FwiThresholdTable(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_RESOURCE_PATH);
    }

    // Test seam: lets tests point the loader at a resource path that does not exist, to prove
    // the "clear exception, not a silent fallback" contract on load failure. Same idiom as
    // MapbiomasSeedLoader's setSeedResourcePath test seam.
    FwiThresholdTable(ObjectMapper objectMapper, String resourcePath) {
        this.thresholdsByMethod = loadFromResource(objectMapper, resourcePath);
    }

    /**
     * @param method the {@code territory.fwi.method} value, e.g. {@code "PROXY_V1"} or
     *     {@code "VAN_WAGNER"}.
     * @throws IllegalArgumentException if {@code method} has no entry in the table -- callers
     *     must not silently fall back to a default method's thresholds.
     */
    public FwiThresholds forMethod(String method) {
        FwiThresholds thresholds = thresholdsByMethod.get(method);
        if (thresholds == null) {
            throw new IllegalArgumentException(
                "No FWI threshold entry for method \"" + method + "\". Known methods: "
                    + thresholdsByMethod.keySet()
            );
        }
        return thresholds;
    }

    private static Map<String, FwiThresholds> loadFromResource(ObjectMapper objectMapper, String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream()) {
            return objectMapper.readValue(is, new TypeReference<Map<String, FwiThresholds>>() {});
        } catch (IOException ex) {
            // Fail fast and loud: unlike MapbiomasSeedLoader (which degrades gracefully on a
            // missing seed because the app can still boot without it), a missing/malformed
            // threshold table means any future consumer would have no thresholds to read at
            // all -- there is no safe default to fall back to here.
            throw new IllegalStateException(
                "Failed to load FWI threshold table from classpath resource \"" + path + "\"", ex
            );
        }
    }
}
