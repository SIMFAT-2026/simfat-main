package com.simfat.backend.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Per-comuna MapBiomas statistics (land cover + fire history), loaded from a committed
 * JSON/JSONL seed by {@code MapbiomasSeedLoader} (S1b2). Lives in its OWN collection,
 * separate from {@code comunas}: {@link ComunaInfo} is re-saved wholesale on every
 * startup by {@code MonitoredComunasConfig.ensureMonitoredComunas()}, so any field it
 * did not explicitly set would survive only by luck (design decision D1).
 *
 * <p>One document per {@code (comunaId, dataVersion)}, deterministic {@code _id =
 * "{comunaId}|{dataVersion}"} (see {@link #buildId}) so re-loading the same seed is an
 * idempotent upsert — never duplicates, safe to re-run on every boot.
 *
 * <p>History note: the FIRST loaded seed (dataVersion {@code fuego-col1@2017-partial})
 * had only Fuego Colección 1 (2017 fire-only) data — {@link #landCover} was {@code null}
 * with {@link #landCoverReason} explaining why. S1b4 (dataVersion {@code
 * lulc-col2+fuego-col1@2017-partial}) merged in REAL MapBiomas Chile Colección 2 land-cover
 * shares for all 86 comunas, years 1999-2024 ({@link LandCover#sharesByYear}); {@link
 * #landCover} is no longer {@code null} and {@link #landCoverReason} is no longer set. Fuego
 * itself was STILL 2017-only (partial) at that point — see {@link #fireReason}.
 *
 * <p>S1b5 (dataVersion {@code lulc-col2+fuego-col1@2013-2025}) replaced the 2017-only fire
 * section with the FULL MapBiomas Chile Fuego Colección 1 history (2013-2025, every year) for
 * all 86 comunas: {@link Fire#burnedHaByYear}/{@link Fire#burnedFractionByYear} now cover every
 * year in that range, {@link Fire#burnedFraction} is the real pixel-level UNION over the whole
 * range (cross-checked against MapBiomas' own {@code accumulated_burned_v1} product), and
 * {@link #fireReason} is no longer set. Both inputs are now the FULL public collections, so
 * {@link #partial} is {@code false}.
 */
@Document(collection = "comuna_mapbiomas_stats")
@CompoundIndexes({
    @CompoundIndex(name = "idx_region_dataversion", def = "{'regionId': 1, 'dataVersion': 1}"),
    @CompoundIndex(name = "idx_comuna_dataversion_desc", def = "{'comunaId': 1, 'dataVersion': -1}")
})
public class ComunaMapbiomasStats {

    @Id
    private String id;

    private String comunaId;
    private String regionId;
    private String nombreComuna;
    private String dataVersion;

    private LandCover landCover;
    private String landCoverReason;

    private Fire fire;
    private String fireReason;

    private boolean partial;

    private Provenance provenance;
    private Instant computedAt;

    /** Deterministic upsert key so re-loading the same seed never duplicates a document. */
    public static String buildId(String comunaId, String dataVersion) {
        return comunaId + "|" + dataVersion;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getComunaId() { return comunaId; }
    public void setComunaId(String comunaId) { this.comunaId = comunaId; }

    public String getRegionId() { return regionId; }
    public void setRegionId(String regionId) { this.regionId = regionId; }

    public String getNombreComuna() { return nombreComuna; }
    public void setNombreComuna(String nombreComuna) { this.nombreComuna = nombreComuna; }

    public String getDataVersion() { return dataVersion; }
    public void setDataVersion(String dataVersion) { this.dataVersion = dataVersion; }

    public LandCover getLandCover() { return landCover; }
    public void setLandCover(LandCover landCover) { this.landCover = landCover; }

    public String getLandCoverReason() { return landCoverReason; }
    public void setLandCoverReason(String landCoverReason) { this.landCoverReason = landCoverReason; }

    public Fire getFire() { return fire; }
    public void setFire(Fire fire) { this.fire = fire; }

    public String getFireReason() { return fireReason; }
    public void setFireReason(String fireReason) { this.fireReason = fireReason; }

    public boolean isPartial() { return partial; }
    public void setPartial(boolean partial) { this.partial = partial; }

    public Provenance getProvenance() { return provenance; }
    public void setProvenance(Provenance provenance) { this.provenance = provenance; }

    public Instant getComputedAt() { return computedAt; }
    public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }

    /** Embedded, present only once a LULC year is processed into this dataVersion's seed. */
    public static class LandCover {
        private Integer referenceYear;
        private Map<String, Double> sharesByClass;
        private Map<String, Double> sharesByClassMean5y;
        private SharesByYear sharesByYear;

        public Integer getReferenceYear() { return referenceYear; }
        public void setReferenceYear(Integer referenceYear) { this.referenceYear = referenceYear; }

        public Map<String, Double> getSharesByClass() { return sharesByClass; }
        public void setSharesByClass(Map<String, Double> sharesByClass) { this.sharesByClass = sharesByClass; }

        public Map<String, Double> getSharesByClassMean5y() { return sharesByClassMean5y; }
        public void setSharesByClassMean5y(Map<String, Double> sharesByClassMean5y) { this.sharesByClassMean5y = sharesByClassMean5y; }

        public SharesByYear getSharesByYear() { return sharesByYear; }
        public void setSharesByYear(SharesByYear sharesByYear) { this.sharesByYear = sharesByYear; }
    }

    /**
     * Per-year, class-major, sparse basis-point series (design D1 rev2, S1b4): {@code
     * classes.get(code)[i]} is the basis-point share (0-10000) of {@code code} in year {@code
     * firstYear + i}. Every array has length {@code lastYear - firstYear + 1}; a class absent
     * from a given year reads 0 at that index; a class never present in ANY year in range is
     * not a key at all (sparse). {@code sharesByClass}/{@code sharesByClassMean5y} on the
     * enclosing {@link LandCover} are FRACTIONS (0-1), never basis points — only this field is
     * bp (design D1's unit distinction, fixed in S1b3 after obs 774's bp-vs-fraction finding).
     */
    public static class SharesByYear {
        private Integer firstYear;
        private Integer lastYear;
        private String unit;
        private Map<String, int[]> classes;

        public Integer getFirstYear() { return firstYear; }
        public void setFirstYear(Integer firstYear) { this.firstYear = firstYear; }

        public Integer getLastYear() { return lastYear; }
        public void setLastYear(Integer lastYear) { this.lastYear = lastYear; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public Map<String, int[]> getClasses() { return classes; }
        public void setClasses(Map<String, int[]> classes) { this.classes = classes; }
    }

    /** Embedded Fuego statistics, matches the shape emitted by mb_pipeline/build_stats.py. */
    public static class Fire {
        private Boolean available;
        private Double coverageFraction;
        private Map<String, Double> burnedHaByYear;
        private Map<String, Double> burnedFractionByYear;
        private Double frequencyMean;
        private Integer frequencyMax;
        private Integer yearLastFire;
        private Integer yearsSinceLastFire;

        // S1b5 (design D1): the CUMULATIVE burned fraction -- a real pixel-level UNION across
        // every discovered annual year (2013-2025), never a sum of the per-year fractions
        // above (which would double-count a pixel that burned in more than one year). Cross-
        // checked against MapBiomas' own accumulated_burned_v1 product at pipeline time. See
        // mb_pipeline/fire_stats.py::union_burned_fraction. Non-null exactly when at least one
        // annual year was processed (mirrors burnedHaByYear's presence).
        private Double burnedFraction;

        // Design D1/D3: empirical percentile rank of the burned fraction across all comunas,
        // frozen with dataVersion (ties take the mean rank, exact zeros pinned to 0.0). See
        // mb_pipeline/build_stats.py::add_burned_fraction_pct. Non-null exactly for comunas
        // with available=true (a comuna excluded from the rank has no value, not a 0.0).
        private Double burnedFractionPct;

        // Design D1/D3: log1p + p95-clip variant of burnedFractionPct, a config-switchable
        // alternative normalization the pre-registered backtest may select instead. See
        // mb_pipeline/build_stats.py::add_burned_fraction_log. Same non-null rule as
        // burnedFractionPct.
        private Double burnedFractionLog;

        // Non-null exactly when available=false, explaining why (e.g. coverage below
        // threshold, or no fire years processed for the comuna); null when available=true.
        // See mb_pipeline/fire_stats.py::build_fire_section for the exact semantics.
        private String reason;

        public Boolean getAvailable() { return available; }
        public void setAvailable(Boolean available) { this.available = available; }

        public Double getCoverageFraction() { return coverageFraction; }
        public void setCoverageFraction(Double coverageFraction) { this.coverageFraction = coverageFraction; }

        public Map<String, Double> getBurnedHaByYear() { return burnedHaByYear; }
        public void setBurnedHaByYear(Map<String, Double> burnedHaByYear) { this.burnedHaByYear = burnedHaByYear; }

        public Map<String, Double> getBurnedFractionByYear() { return burnedFractionByYear; }
        public void setBurnedFractionByYear(Map<String, Double> burnedFractionByYear) { this.burnedFractionByYear = burnedFractionByYear; }

        public Double getFrequencyMean() { return frequencyMean; }
        public void setFrequencyMean(Double frequencyMean) { this.frequencyMean = frequencyMean; }

        public Integer getFrequencyMax() { return frequencyMax; }
        public void setFrequencyMax(Integer frequencyMax) { this.frequencyMax = frequencyMax; }

        public Integer getYearLastFire() { return yearLastFire; }
        public void setYearLastFire(Integer yearLastFire) { this.yearLastFire = yearLastFire; }

        public Integer getYearsSinceLastFire() { return yearsSinceLastFire; }
        public void setYearsSinceLastFire(Integer yearsSinceLastFire) { this.yearsSinceLastFire = yearsSinceLastFire; }

        public Double getBurnedFraction() { return burnedFraction; }
        public void setBurnedFraction(Double burnedFraction) { this.burnedFraction = burnedFraction; }

        public Double getBurnedFractionPct() { return burnedFractionPct; }
        public void setBurnedFractionPct(Double burnedFractionPct) { this.burnedFractionPct = burnedFractionPct; }

        public Double getBurnedFractionLog() { return burnedFractionLog; }
        public void setBurnedFractionLog(Double burnedFractionLog) { this.burnedFractionLog = burnedFractionLog; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /** Embedded provenance, matches mb_pipeline output (sources/scope/downloadDate). */
    public static class Provenance {
        private List<String> sources;
        private String scope;
        private String downloadDate;

        public List<String> getSources() { return sources; }
        public void setSources(List<String> sources) { this.sources = sources; }

        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }

        public String getDownloadDate() { return downloadDate; }
        public void setDownloadDate(String downloadDate) { this.downloadDate = downloadDate; }
    }
}
