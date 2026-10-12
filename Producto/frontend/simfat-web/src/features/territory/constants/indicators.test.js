import { describe, expect, it } from 'vitest';
import {
  CRITICAL_INDICATORS,
  DEFAULT_INDICATORS,
  DEFAULT_VISIBLE_INDICATORS,
  MAPBIOMAS_INDICATORS,
  SECONDARY_INDICATORS
} from './indicators';

describe('indicator groups', () => {
  it('accepts BURNED and LANDCOVER as initial visible indicators (deep links)', () => {
    expect(DEFAULT_INDICATORS).toEqual(expect.arrayContaining(['BURNED', 'LANDCOVER']));
  });

  it('keeps every previous default indicator', () => {
    expect(DEFAULT_INDICATORS).toEqual(
      expect.arrayContaining(['RISK_SCORE', 'ALERTS', 'FIRMS', 'NDVI', 'NDMI', 'REPORTS', 'WIND', 'HUMIDITY', 'AIR_TEMP', 'SOIL_TEMP'])
    );
  });

  it('never bulk-loads MapBiomas layers with the region (they are fetched per year)', () => {
    MAPBIOMAS_INDICATORS.forEach((indicator) => {
      expect(SECONDARY_INDICATORS).not.toContain(indicator);
      expect(CRITICAL_INDICATORS).not.toContain(indicator);
    });
  });

  it('does not show MapBiomas layers by default', () => {
    MAPBIOMAS_INDICATORS.forEach((indicator) => {
      expect(DEFAULT_VISIBLE_INDICATORS).not.toContain(indicator);
    });
    expect(DEFAULT_VISIBLE_INDICATORS).toEqual(['RISK_SCORE', 'FIRMS', 'ALERTS', 'REPORTS']);
  });

  it('has no duplicates in the default list', () => {
    expect(new Set(DEFAULT_INDICATORS).size).toBe(DEFAULT_INDICATORS.length);
  });
});
