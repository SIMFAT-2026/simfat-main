package com.simfat.backend.service.fwi;

/**
 * The full set of Canadian Forest Fire Weather Index outputs for one day: the three carried-state
 * codes (also exposed here so callers do not need to reconstruct {@link FwiState} separately) plus
 * the three derived indices and the Daily Severity Rating.
 */
public record FwiOutputs(
        double ffmc, double dmc, double dc, double isi, double bui, double fwi, double dsr) {}
