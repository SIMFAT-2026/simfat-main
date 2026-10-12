package com.simfat.backend.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Persisted Canadian Forest Fire Weather Index (FWI) chain state for one comuna (S1d1).
 *
 * <p>One document per comuna ({@code _id = comunaId}) holding only the CURRENT/latest state,
 * not a history: {@code stateDate}/{@code ffmc}/{@code dmc}/{@code dc} are the codes at the END
 * of {@code stateDate}, and {@code baseDate}/{@code base*} are the codes at the end of the day
 * BEFORE that. The base fields exist specifically so a same-calendar-day re-sync (the service's
 * daily cron fires twice, see {@code com.simfat.backend.service.fwi.ComunaFwiStateService}) can
 * recompute "today" from "yesterday's base" instead of compounding an already-advanced value.
 *
 * <p>Design decision D4 (see {@code sdd/mapbiomas-integration/design}) also describes a
 * season-start rule and a DC overwintering procedure with their own state fields
 * ({@code seasonStartedOn}, {@code lastOverwinteredOn}). Those are deliberately NOT implemented
 * by this slice (S1d1) — this document is scoped down to persistence + idempotent daily advance
 * only. If a later slice adds them, they can be added as new nullable fields without a schema
 * migration (old documents read {@code null}), the same additive pattern already used elsewhere
 * in this change (e.g. {@code ComunaRiskSnapshot}'s MapBiomas fields).
 */
@Document(collection = "comuna_fwi_state")
public class ComunaFwiState {

    /** The comunaId. */
    @Id
    private String id;

    /** The calendar date the chain has been advanced THROUGH. */
    private LocalDate stateDate;

    /** Fine Fuel Moisture Code at the end of {@link #stateDate}. */
    private Double ffmc;

    /** Duff Moisture Code at the end of {@link #stateDate}. */
    private Double dmc;

    /** Drought Code at the end of {@link #stateDate}. */
    private Double dc;

    /** The calendar date one day before {@link #stateDate} — the "base" the chain advances from. */
    private LocalDate baseDate;

    /** Fine Fuel Moisture Code at the end of {@link #baseDate}. */
    private Double baseFfmc;

    /** Duff Moisture Code at the end of {@link #baseDate}. */
    private Double baseDmc;

    /** Drought Code at the end of {@link #baseDate}. */
    private Double baseDc;

    /** Always {@code "VAN_WAGNER"} for now; a field, not a constant, so a future method change
     * (or a proxy-replay fallback) is representable without a schema migration. */
    private String method;

    /**
     * {@code null} in the normal case; {@code "FWI_WARMUP"} the first time a comuna's chain is
     * started from published startup values; {@code "FWI_RESTARTED"} when a gap exceeded the
     * configured threshold and the chain was forcibly restarted from startup values instead of
     * being replayed. Cleared back to {@code null} on the next normal daily advance.
     */
    private String qualityFlag;

    /** Observability only — not read by any advance-orchestration logic. */
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDate getStateDate() {
        return stateDate;
    }

    public void setStateDate(LocalDate stateDate) {
        this.stateDate = stateDate;
    }

    public Double getFfmc() {
        return ffmc;
    }

    public void setFfmc(Double ffmc) {
        this.ffmc = ffmc;
    }

    public Double getDmc() {
        return dmc;
    }

    public void setDmc(Double dmc) {
        this.dmc = dmc;
    }

    public Double getDc() {
        return dc;
    }

    public void setDc(Double dc) {
        this.dc = dc;
    }

    public LocalDate getBaseDate() {
        return baseDate;
    }

    public void setBaseDate(LocalDate baseDate) {
        this.baseDate = baseDate;
    }

    public Double getBaseFfmc() {
        return baseFfmc;
    }

    public void setBaseFfmc(Double baseFfmc) {
        this.baseFfmc = baseFfmc;
    }

    public Double getBaseDmc() {
        return baseDmc;
    }

    public void setBaseDmc(Double baseDmc) {
        this.baseDmc = baseDmc;
    }

    public Double getBaseDc() {
        return baseDc;
    }

    public void setBaseDc(Double baseDc) {
        this.baseDc = baseDc;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getQualityFlag() {
        return qualityFlag;
    }

    public void setQualityFlag(String qualityFlag) {
        this.qualityFlag = qualityFlag;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
