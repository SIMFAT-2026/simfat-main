import { describe, expect, it } from 'vitest';
import { landcoverFillOpacity } from './mapbiomasStyle';

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
