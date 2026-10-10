import { describe, expect, it } from 'vitest';
import { burnedFillOpacity, landcoverFillOpacity } from './mapbiomasStyle';

describe('landcoverFillOpacity', () => {
  it('uses the regular opacity for a comuna with data', () => {
    expect(landcoverFillOpacity({ hasData: true, riskVisible: false })).toBe(0.6);
  });

  it('lowers the opacity when RISK_SCORE is visible so its colours read through', () => {
    expect(landcoverFillOpacity({ hasData: true, riskVisible: true })).toBe(0.35);
  });

  it('keeps the faint neutral fill for comunas without data, with or without risk', () => {
    expect(landcoverFillOpacity({ hasData: false, riskVisible: false })).toBe(0.12);
    expect(landcoverFillOpacity({ hasData: false, riskVisible: true })).toBe(0.12);
  });
});

describe('stale (previous year) dimming', () => {
  it('dims LANDCOVER data fills while the layer is stale, with or without risk', () => {
    expect(landcoverFillOpacity({ hasData: true, riskVisible: false, stale: true })).toBeCloseTo(0.3);
    expect(landcoverFillOpacity({ hasData: true, riskVisible: true, stale: true })).toBeCloseTo(0.175);
  });

  it('does not touch the no-data fill when stale', () => {
    expect(landcoverFillOpacity({ hasData: false, riskVisible: false, stale: true })).toBe(0.12);
  });
});

describe('burnedFillOpacity', () => {
  it('uses 0.55 with data and the faint fill without', () => {
    expect(burnedFillOpacity({ hasData: true })).toBe(0.55);
    expect(burnedFillOpacity({ hasData: false })).toBe(0.12);
  });

  it('dims the data fill while stale', () => {
    expect(burnedFillOpacity({ hasData: true, stale: true })).toBeCloseTo(0.275);
    expect(burnedFillOpacity({ hasData: false, stale: true })).toBe(0.12);
  });
});
