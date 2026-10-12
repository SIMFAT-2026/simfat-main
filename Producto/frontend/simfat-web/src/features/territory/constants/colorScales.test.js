import { describe, expect, it } from 'vitest';
import { ALERT_LEVEL_CONFIG, BURNED_SCALE, NEUTRAL_FILL, burnedColorFor } from './colorScales';

const available = (burnedFraction) => ({ available: true, burnedFraction, burnedHa: 1 });
const colorOfBin = (index) => BURNED_SCALE.bins[index].color;

describe('burnedColorFor', () => {
  it('uses the neutral no-data colour when the entry is missing or unavailable', () => {
    expect(burnedColorFor(undefined)).toBe(NEUTRAL_FILL);
    expect(burnedColorFor(null)).toBe(NEUTRAL_FILL);
    expect(burnedColorFor({ available: false, burnedFraction: 0.1 })).toBe(NEUTRAL_FILL);
    expect(burnedColorFor({ burnedFraction: 0.1 })).toBe(NEUTRAL_FILL);
  });

  it('uses the neutral colour for a null or non-finite fraction', () => {
    expect(burnedColorFor(available(null))).toBe(NEUTRAL_FILL);
    expect(burnedColorFor(available(undefined))).toBe(NEUTRAL_FILL);
    expect(burnedColorFor(available(Number.NaN))).toBe(NEUTRAL_FILL);
    expect(burnedColorFor(available(Infinity))).toBe(NEUTRAL_FILL);
    expect(burnedColorFor(available(-0.1))).toBe(NEUTRAL_FILL);
  });

  it('distinguishes an observed zero from no data', () => {
    expect(burnedColorFor(available(0))).toBe(BURNED_SCALE.zero.color);
    expect(BURNED_SCALE.zero.color).not.toBe(NEUTRAL_FILL);
  });

  it('puts every positive fraction below 0.5 % in the first bin', () => {
    expect(burnedColorFor(available(0.000001))).toBe('#D0AE35');
    expect(burnedColorFor(available(0.00499))).toBe('#D0AE35');
  });

  it('places exact bin edges in the upper bin', () => {
    expect(burnedColorFor(available(0.005))).toBe('#C68E2F');
    expect(burnedColorFor(available(0.02))).toBe('#B96D2B');
    expect(burnedColorFor(available(0.05))).toBe('#AC4B28');
    expect(burnedColorFor(available(0.2))).toBe('#72251D');
  });

  it('keeps the values just below each edge in the lower bin', () => {
    expect(burnedColorFor(available(0.0199))).toBe('#C68E2F');
    expect(burnedColorFor(available(0.0499))).toBe('#B96D2B');
    expect(burnedColorFor(available(0.1999))).toBe('#AC4B28');
  });

  it('uses the last bin up to a full burn', () => {
    expect(burnedColorFor(available(0.53))).toBe(colorOfBin(4));
    expect(burnedColorFor(available(1))).toBe(colorOfBin(4));
  });
});

describe('BURNED_SCALE', () => {
  it('has five ascending bins ending at Infinity with Spanish labels', () => {
    const maxes = BURNED_SCALE.bins.map((bin) => bin.max);
    expect(maxes).toEqual([0.005, 0.02, 0.05, 0.2, Infinity]);
    expect(BURNED_SCALE.bins.map((bin) => bin.label)).toEqual([
      '< 0,5 %',
      '0,5–2 %',
      '2–5 %',
      '5–20 %',
      '> 20 %'
    ]);
    expect(BURNED_SCALE.zero.label).toBe('Sin área quemada');
  });
});

describe('ALERT_LEVEL_CONFIG legend text', () => {
  it('states the score range and the trigger for each level in visible text', () => {
    expect(Object.values(ALERT_LEVEL_CONFIG).map((item) => item.legendText)).toEqual([
      'Normal (puntaje 0–49)',
      'Preventivo (puntaje 50–69)',
      'Alto (puntaje 70–84 o FWI ≥ 20)',
      'Crítico (puntaje ≥ 85, FWI ≥ 45 o focos FIRMS)'
    ]);
  });
});
