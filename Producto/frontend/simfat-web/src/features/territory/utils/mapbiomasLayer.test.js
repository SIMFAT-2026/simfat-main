import { describe, expect, it } from 'vitest';
import {
  buildMapbiomasParams,
  burnedSummary,
  formatFractionAsPercent,
  mapbiomasCacheKey
} from './mapbiomasLayer';

describe('formatFractionAsPercent', () => {
  it('formats a fraction as a Spanish-Chile percentage', () => {
    expect(formatFractionAsPercent(0.0247)).toBe('2,47 %');
    expect(formatFractionAsPercent(0.53)).toBe('53,00 %');
    expect(formatFractionAsPercent(1)).toBe('100,00 %');
  });

  it('formats an exact zero as 0,00 %', () => {
    expect(formatFractionAsPercent(0)).toBe('0,00 %');
  });

  it('never rounds a positive fraction down to a misleading 0,00 %', () => {
    expect(formatFractionAsPercent(0.00001)).toBe('< 0,01 %');
    expect(formatFractionAsPercent(0.0001)).toBe('0,01 %');
  });

  it('returns a no-data label for missing or non-finite values', () => {
    expect(formatFractionAsPercent(null)).toBe('Sin datos');
    expect(formatFractionAsPercent(undefined)).toBe('Sin datos');
    expect(formatFractionAsPercent(Number.NaN)).toBe('Sin datos');
    expect(formatFractionAsPercent('abc')).toBe('Sin datos');
  });
});

describe('burnedSummary', () => {
  const layer = {
    values: {
      a: { available: true, burnedFraction: 0.0247, burnedHa: 10 },
      b: { available: true, burnedFraction: 0.0001, burnedHa: 1 },
      c: { available: true, burnedFraction: 0, burnedHa: 0 },
      d: { available: true, burnedFraction: 0, burnedHa: 0 },
      e: { available: true, burnedFraction: 0, burnedHa: 0 },
      f: { available: false, burnedFraction: null, burnedHa: null }
    }
  };

  it('counts burned, zero and unavailable comunas', () => {
    expect(burnedSummary(layer)).toEqual({ burned: 2, zero: 3, unavailable: 1, total: 6 });
  });

  it('treats available:false as unavailable even with a stray fraction', () => {
    const odd = { values: { a: { available: false, burnedFraction: 0.5 } } };
    expect(burnedSummary(odd)).toEqual({ burned: 0, zero: 0, unavailable: 1, total: 1 });
  });

  it('treats available:true with a non-numeric fraction as unavailable', () => {
    const odd = { values: { a: { available: true, burnedFraction: null } } };
    expect(burnedSummary(odd)).toEqual({ burned: 0, zero: 0, unavailable: 1, total: 1 });
  });

  it('returns zero counts for a missing layer', () => {
    expect(burnedSummary(null)).toEqual({ burned: 0, zero: 0, unavailable: 0, total: 0 });
    expect(burnedSummary({})).toEqual({ burned: 0, zero: 0, unavailable: 0, total: 0 });
  });
});

describe('mapbiomasCacheKey', () => {
  const base = { indicator: 'BURNED', year: 2024, regionId: 'CL-BI', publicMode: false };

  it('never shares a key between different years', () => {
    const years = [2013, 2020, 2024, 2025];
    const keys = years.map((year) => mapbiomasCacheKey({ ...base, year }));
    expect(new Set(keys).size).toBe(years.length);
  });

  it('separates indicators, regions and public/auth mode', () => {
    const keys = [
      mapbiomasCacheKey(base),
      mapbiomasCacheKey({ ...base, indicator: 'LANDCOVER' }),
      mapbiomasCacheKey({ ...base, regionId: 'CL-AR' }),
      mapbiomasCacheKey({ ...base, publicMode: true })
    ];
    expect(new Set(keys).size).toBe(4);
  });

  it('uses a distinct "default" slot when no year is given', () => {
    const noYear = mapbiomasCacheKey({ ...base, year: undefined });
    expect(noYear).toContain('default');
    expect(noYear).not.toBe(mapbiomasCacheKey({ ...base, year: 2025 }));
    expect(mapbiomasCacheKey({ ...base, year: null })).toBe(noYear);
  });

  it('is stable for equal inputs', () => {
    expect(mapbiomasCacheKey(base)).toBe(mapbiomasCacheKey({ ...base }));
  });
});

describe('buildMapbiomasParams', () => {
  it('sends the year when given', () => {
    expect(buildMapbiomasParams({ regionId: 'CL-BI', indicator: 'LANDCOVER', year: 2024 })).toEqual({
      regionId: 'CL-BI',
      indicators: 'LANDCOVER',
      year: 2024
    });
  });

  it('omits the year key when undefined or null so the backend default applies', () => {
    const undef = buildMapbiomasParams({ regionId: 'CL-BI', indicator: 'BURNED' });
    const nul = buildMapbiomasParams({ regionId: 'CL-BI', indicator: 'BURNED', year: null });
    expect('year' in undef).toBe(false);
    expect('year' in nul).toBe(false);
  });

  it('rejects anything that is not a MapBiomas indicator', () => {
    expect(() => buildMapbiomasParams({ regionId: 'CL-BI', indicator: 'WIND' })).toThrow(/MapBiomas/);
  });
});
