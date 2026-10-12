import { describe, expect, it } from 'vitest';
import { normalizeLayerPayload } from './layerPayload';
import { CLIMATE_INDICATORS, MAPBIOMAS_INDICATORS, VALUE_MAP_INDICATORS } from '../constants/indicators';

// Shapes mirror MapbiomasBurnedLayerBuilder / MapbiomasLandCoverLayerBuilder.
const burnedLayer = {
  indicator: 'BURNED',
  unit: 'fraction',
  year: 2025,
  minYear: 2013,
  maxYear: 2025,
  dataVersion: 'mapbiomas-2025.1',
  citation: 'MapBiomas Fuego Chile, Collection 1',
  values: {
    'CHL.6.1.1_1': { available: true, burnedFraction: 0.0247, burnedHa: 1234.5 },
    'CHL.6.2.1_1': { available: true, burnedFraction: 0, burnedHa: 0 },
    'CHL.6.3.1_1': { available: false, burnedFraction: null, burnedHa: null }
  }
};

const landCoverLayer = {
  indicator: 'LANDCOVER',
  unit: 'fraction',
  year: 2024,
  minYear: 1999,
  maxYear: 2024,
  dataVersion: 'mapbiomas-2025.1',
  citation: 'MapBiomas Chile, Collection 3',
  values: {
    'CHL.6.1.1_1': {
      available: true,
      dominantClass: 9,
      dominantLabel: 'Silvicultura',
      dominantShare: 0.6045,
      top: [
        { code: 9, label: 'Silvicultura', share: 0.6045 },
        { code: 15, label: 'Pastura', share: 0.1463 },
        { code: 60, label: 'Bosque Secundario', share: 0.0799 }
      ],
      unobservedShare: 0
    }
  }
};

describe('indicator constants', () => {
  it('keeps the climate list unchanged', () => {
    expect(CLIMATE_INDICATORS).toEqual(['WIND', 'HUMIDITY', 'AIR_TEMP', 'SOIL_TEMP']);
  });

  it('lists the MapBiomas value-map indicators', () => {
    expect(MAPBIOMAS_INDICATORS).toEqual(['BURNED', 'LANDCOVER']);
  });

  it('exposes climate and MapBiomas indicators as value maps without duplicates', () => {
    expect(VALUE_MAP_INDICATORS).toEqual([...CLIMATE_INDICATORS, ...MAPBIOMAS_INDICATORS]);
    expect(new Set(VALUE_MAP_INDICATORS).size).toBe(VALUE_MAP_INDICATORS.length);
  });
});

describe('normalizeLayerPayload', () => {
  it('keeps the BURNED payload intact including year metadata', () => {
    const result = normalizeLayerPayload({ regionId: 'CL-BI', layers: { BURNED: burnedLayer } }, 'CL-BI');
    expect(result.layers.BURNED).toEqual(burnedLayer);
    expect(result.layers.BURNED.year).toBe(2025);
    expect(result.layers.BURNED.minYear).toBe(2013);
    expect(result.layers.BURNED.maxYear).toBe(2025);
    expect(result.layers.BURNED.unit).toBe('fraction');
    expect(result.layers.BURNED.citation).toBeTruthy();
    expect(result.layers.BURNED.dataVersion).toBeTruthy();
  });

  it('keeps the LANDCOVER payload intact including the top classes', () => {
    const result = normalizeLayerPayload({ data: { layers: { LANDCOVER: landCoverLayer } } }, 'CL-BI');
    expect(result.layers.LANDCOVER).toEqual(landCoverLayer);
    expect(result.layers.LANDCOVER.values['CHL.6.1.1_1'].top).toHaveLength(3);
  });

  it('still keeps climate value maps and ignores unknown layers', () => {
    const wind = { indicator: 'WIND', unit: 'km/h', values: { a: { value: 3 } } };
    const result = normalizeLayerPayload({ layers: { WIND: wind, MYSTERY: { values: { a: 1 } } } }, 'CL-BI');
    expect(result.layers.WIND).toEqual(wind);
    expect(result.layers.MYSTERY).toBeUndefined();
  });

  it('omits MapBiomas layers that are absent or not value maps', () => {
    const result = normalizeLayerPayload({ layers: { BURNED: 'oops', LANDCOVER: null } }, 'CL-BI');
    expect('BURNED' in result.layers).toBe(false);
    expect('LANDCOVER' in result.layers).toBe(false);
  });

  it('keeps the empty feature collections for the geojson layers', () => {
    const result = normalizeLayerPayload({ layers: {} }, 'CL-BI');
    expect(result.regionId).toBe('CL-BI');
    expect(result.layers.NDVI).toEqual({ type: 'FeatureCollection', features: [] });
  });
});
