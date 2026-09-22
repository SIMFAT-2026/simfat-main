package com.simfat.backend.service.fwi;

/**
 * Result of {@link ComunaFwiStateService#advance}: the full FWI outputs for the target date, plus
 * an optional quality flag describing an exceptional event in how the state was derived (a cold
 * start or a post-gap restart). {@code null} in the normal daily-advance case. A same-day resync
 * PRESERVES whatever flag (if any) was already set earlier the same calendar day, rather than
 * always clearing it.
 */
public record ComunaFwiAdvanceResult(FwiOutputs outputs, String qualityFlag) {}
