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
 * <p>Honesty note: as of the first loaded seed (dataVersion {@code fuego-col1@2017-partial}),
 * only Fuego Colección 1 (2017 fire-only) data exists — {@link #landCover} is {@code null}
 * with {@link #landCoverReason} explaining why, and {@link #partial} is {@code true}. The
 * shape supports a future merged LULC+Fuego seed without a schema migration.
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

        public Integer getReferenceYear() { return referenceYear; }
        public void setReferenceYear(Integer referenceYear) { this.referenceYear = referenceYear; }

        public Map<String, Double> getSharesByClass() { return sharesByClass; }
        public void setSharesByClass(Map<String, Double> sharesByClass) { this.sharesByClass = sharesByClass; }
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
