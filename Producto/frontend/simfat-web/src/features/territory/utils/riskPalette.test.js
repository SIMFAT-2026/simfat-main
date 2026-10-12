import { describe, expect, it } from 'vitest';
import { ALERT_LEVEL_CONFIG, RISK_FILL_OPACITY, RISK_NO_DATA } from '../constants/colorScales';
import {
  LAND_COLOR,
  blendOver,
  blendedLevelFills,
  comunaRiskStyle,
  deltaE2000,
  hexDeltaE,
  riskLegendItems
} from './riskPalette';

const ORDER = ['NORMAL', 'PREVENTIVO', 'ALTO', 'CRITICO'];
const MIN_DELTA_E = 20;

describe('blendOver', () => {
  it('composites a colour over a background at the given opacity', () => {
    expect(blendOver('#000000', '#ffffff', 0.5)).toBe('#808080');
    expect(blendOver('#ff0000', '#ffffff', 1)).toBe('#ff0000');
    expect(blendOver('#ff0000', '#ffffff', 0)).toBe('#ffffff');
  });
});

describe('deltaE2000', () => {
  it('matches the Sharma reference pairs', () => {
    expect(deltaE2000([50, 2.6772, -79.7751], [50, 0, -82.7485])).toBeCloseTo(2.0425, 3);
    expect(deltaE2000([50, 2.5, 0], [73, 25, -18])).toBeCloseTo(27.1492, 3);
  });

  it('is zero for identical colours', () => {
    expect(hexDeltaE('#abcdef', '#abcdef')).toBeCloseTo(0, 6);
  });
});

describe('risk palette distinguishability on the map', () => {
  const fills = blendedLevelFills();

  it('blends every level over the OSM land colour with the map fill opacity', () => {
    for (const level of ORDER) {
      expect(fills[level]).toBe(blendOver(ALERT_LEVEL_CONFIG[level].fill, LAND_COLOR, RISK_FILL_OPACITY));
    }
  });

  it('keeps adjacent levels at least 20 delta E apart after blending', () => {
    for (let i = 0; i < ORDER.length - 1; i += 1) {
      expect(hexDeltaE(fills[ORDER[i]], fills[ORDER[i + 1]])).toBeGreaterThanOrEqual(MIN_DELTA_E);
    }
  });

  it('keeps the lowest level distinguishable from bare land', () => {
    expect(hexDeltaE(fills.NORMAL, LAND_COLOR)).toBeGreaterThanOrEqual(MIN_DELTA_E);
  });

  it('keeps "sin datos" at least 20 delta E from every level', () => {
    for (const level of ORDER) {
      expect(hexDeltaE(fills.NO_DATA, fills[level])).toBeGreaterThanOrEqual(MIN_DELTA_E);
    }
  });

  it('keeps green to red semantics (hue moves from green through yellow to red)', () => {
    const channel = (hex, i) => parseInt(hex.slice(1 + i * 2, 3 + i * 2), 16);
    const normal = ALERT_LEVEL_CONFIG.NORMAL.fill;
    const critico = ALERT_LEVEL_CONFIG.CRITICO.fill;
    expect(channel(normal, 1)).toBeGreaterThan(channel(normal, 0));
    expect(channel(critico, 0)).toBeGreaterThan(channel(critico, 1));
  });
});

describe('comunaRiskStyle', () => {
  it('uses the shared fill opacity and the level stroke when there is a score', () => {
    const style = comunaRiskStyle({ alertLevel: 'ALTO' });
    expect(style.fillColor).toBe(ALERT_LEVEL_CONFIG.ALTO.fill);
    expect(style.fillOpacity).toBe(RISK_FILL_OPACITY);
    expect(style.color).toBe(ALERT_LEVEL_CONFIG.ALTO.color);
    expect(style.dashArray).toBe(ALERT_LEVEL_CONFIG.ALTO.dashArray);
  });

  it('falls back to Normal for a score without a known level', () => {
    expect(comunaRiskStyle({}).fillColor).toBe(ALERT_LEVEL_CONFIG.NORMAL.fill);
  });

  it('draws "sin datos" with its own grey fill and a dotted outline', () => {
    const style = comunaRiskStyle(undefined);
    expect(style.fillColor).toBe(RISK_NO_DATA.fill);
    expect(style.fillOpacity).toBe(RISK_NO_DATA.fillOpacity);
    expect(style.dashArray).toBe(RISK_NO_DATA.dashArray);
    for (const level of ORDER) {
      expect(style.fillColor).not.toBe(ALERT_LEVEL_CONFIG[level].fill);
    }
  });
});

describe('riskLegendItems', () => {
  const items = riskLegendItems();
  const fills = blendedLevelFills();

  it('lists the four levels followed by "sin datos"', () => {
    expect(items.map((item) => item.key)).toEqual([...ORDER, 'NO_DATA']);
    expect(items[4].text).toBe('Sin datos de riesgo');
  });

  it('paints each swatch with the exact colour the map shows over land', () => {
    for (const item of items) {
      expect(item.swatchFill).toBe(fills[item.key]);
    }
  });

  it('mirrors the map outline style in the swatch border', () => {
    expect(items[0].borderStyle).toBe('solid');
    expect(items[1].borderStyle).toBe('dashed');
    expect(items[4].borderStyle).toBe('dotted');
  });
});
