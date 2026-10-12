import { describe, expect, it } from 'vitest';
import {
  CRITICAL_INDICATORS,
  DEFAULT_INDICATORS,
  DEFAULT_VISIBLE_INDICATORS,
  LAYER_TOGGLE_GROUPS,
  MAPBIOMAS_INDICATORS,
  SECONDARY_INDICATORS,
  activeLayerSummary,
  layerGroupInfoLines
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

describe('LAYER_TOGGLE_GROUPS', () => {
  const toggleIndicators = [
    'RISK_SCORE', 'FIRMS', 'ALERTS', 'REPORTS', 'BURNED', 'LANDCOVER',
    'NDVI', 'NDMI', 'WIND', 'HUMIDITY', 'AIR_TEMP', 'SOIL_TEMP'
  ];

  it('orders the groups by time semantics: events, MapBiomas, current conditions', () => {
    expect(LAYER_TOGGLE_GROUPS.map((group) => group.title)).toEqual([
      'Riesgo y eventos',
      'MapBiomas',
      'Condiciones actuales'
    ]);
    expect(LAYER_TOGGLE_GROUPS.map((group) => group.indicators)).toEqual([
      ['RISK_SCORE', 'FIRMS', 'ALERTS', 'REPORTS'],
      ['BURNED', 'LANDCOVER'],
      ['NDVI', 'NDMI', 'WIND', 'HUMIDITY', 'AIR_TEMP', 'SOIL_TEMP']
    ]);
  });

  it('lists every toggle indicator exactly once', () => {
    const all = LAYER_TOGGLE_GROUPS.flatMap((group) => group.indicators);
    expect([...all].sort()).toEqual([...toggleIndicators].sort());
  });

  it('keeps events and MapBiomas inline and moves current conditions into the popover', () => {
    expect(LAYER_TOGGLE_GROUPS.map((group) => group.placement)).toEqual(['inline', 'inline', 'popover']);
  });

  it('gives the popover group its context line', () => {
    expect(LAYER_TOGGLE_GROUPS[2].context).toBe('Última observación satelital y climática');
  });
});

describe('layerGroupInfoLines', () => {
  it('explains each group, including that risk ignores the MapBiomas year', () => {
    expect(layerGroupInfoLines()).toEqual([
      'Riesgo y eventos: situación actual. El riesgo es el valor actual; no depende del año elegido en las capas MapBiomas.',
      'MapBiomas: historia anual (elige el año junto a cada capa).',
      'Más capas: última observación satelital y climática.'
    ]);
  });
});

describe('activeLayerSummary', () => {
  const group = LAYER_TOGGLE_GROUPS[2];

  it('is empty when nothing in the group is visible', () => {
    expect(activeLayerSummary({ group, visibleIndicators: ['RISK_SCORE'] })).toBe('');
  });

  it('counts the visible layers of the group', () => {
    expect(activeLayerSummary({ group, visibleIndicators: ['WIND'] })).toBe('(1 activa)');
    expect(activeLayerSummary({ group, visibleIndicators: ['WIND', 'NDVI', 'RISK_SCORE'] })).toBe('(2 activas)');
  });
});
