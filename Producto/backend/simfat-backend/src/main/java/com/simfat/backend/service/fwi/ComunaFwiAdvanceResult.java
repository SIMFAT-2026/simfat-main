package com.simfat.backend.service.fwi;

/**
 * Result of {@link ComunaFwiStateService#advance}: the full FWI outputs for the target date, plus
 * an optional quality flag describing an exceptional event in how the state was derived (a cold
 * start or a post-gap restart). {@code null} in the normal daily-advance and same-day-resync
 * cases.
 */
public record ComunaFwiAdvanceResult(FwiOutputs outputs, String qualityFlag) {}
