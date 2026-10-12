import { describe, expect, it } from 'vitest';
import { CLIMATE_SCALES } from '../constants/colorScales';
import { RISK_FILL_OPACITY } from '../constants/colorScales';
import { LAND_COLOR, blendOver, contrastRatio } from './riskPalette';
import { WIND_ARROW, WIND_ARROW_LEGEND, windArrowHtml, windArrowSize } from './windArrow';

describe('windArrowSize', () => {
  it('grows with each wind speed class', () => {
    const sizes = [5, 15, 25, 40, 60].map(windArrowSize);
    expect(sizes).toEqual([...sizes].sort((a, b) => a - b));
    expect(new Set(sizes).size).toBe(5);
  });

  it('puts class edges in the upper class, like the choropleth bins', () => {
    expect(windArrowSize(10)).toBe(windArrowSize(15));
    expect(windArrowSize(9.99)).toBeLessThan(windArrowSize(10));
  });

  it('falls back to the smallest arrow for a missing speed', () => {
    expect(windArrowSize(null)).toBe(windArrowSize(0));
    expect(windArrowSize(Number.NaN)).toBe(windArrowSize(0));
  });
});

describe('windArrowHtml', () => {
  it('uses the fixed dark fill with a white halo, never the speed colour', () => {
    const html = windArrowHtml(45, 90);
    expect(html).toContain(`fill="${WIND_ARROW.fill}"`);
    expect(html).toContain(`stroke="${WIND_ARROW.halo}"`);
    for (const bin of CLIMATE_SCALES.WIND.bins) {
      expect(html).not.toContain(bin.color);
    }
  });

  it('rotates to the bearing the wind is heading to and sizes by speed', () => {
    const html = windArrowHtml(45, 135);
    expect(html).toContain('rotate(135deg)');
    expect(html).toContain(`width="${windArrowSize(45)}"`);
  });
});

describe('arrow readability over every wind fill', () => {
  it('has a halo that separates the dark body from any background', () => {
    expect(contrastRatio(WIND_ARROW.fill, WIND_ARROW.halo)).toBeGreaterThanOrEqual(7);
  });

  it('keeps either the body or the halo at 3:1 or more over each blended wind fill', () => {
    for (const bin of CLIMATE_SCALES.WIND.bins) {
      const background = blendOver(bin.color, LAND_COLOR, RISK_FILL_OPACITY);
      const best = Math.max(
        contrastRatio(WIND_ARROW.fill, background),
        contrastRatio(WIND_ARROW.halo, background)
      );
      expect(best).toBeGreaterThanOrEqual(3);
    }
  });
});

describe('WIND_ARROW_LEGEND', () => {
  it('pairs each speed class label with its arrow size', () => {
    expect(WIND_ARROW_LEGEND.map((item) => item.label)).toEqual(
      CLIMATE_SCALES.WIND.bins.map((bin) => bin.label)
    );
    expect(WIND_ARROW_LEGEND.map((item) => item.size)).toEqual([14, 18, 22, 26, 30]);
  });
});
