// Pure helpers that turn raw /layers responses into the shape the territory
// UI consumes. Kept free of axios/env imports so they are unit-testable.

// Climate indicators are exposed as comuna-keyed value maps:
// { indicator, unit, values: { comunaId: { value, unit, observedAt } } }
// instead of GeoJSON FeatureCollections — they are joined onto the
// comunal GeoJSON client-side (see TerritoryMapPanel ClimateChoropleth).
const CLIMATE_INDICATORS = ['WIND', 'HUMIDITY', 'AIR_TEMP', 'SOIL_TEMP'];

export function extractResponseData(payload) {
  if (payload && typeof payload === 'object' && 'data' in payload) {
    return payload.data;
  }
  return payload;
}

function isFeatureCollection(value) {
  return Boolean(value) && typeof value === 'object' && value.type === 'FeatureCollection';
}

function toFeatureCollection(value) {
  if (isFeatureCollection(value)) {
    return value;
  }

  return {
    type: 'FeatureCollection',
    features: []
  };
}

function isValueMap(value) {
  return Boolean(value) && typeof value === 'object' && value.values && typeof value.values === 'object';
}

function toValueMap(value) {
  if (isValueMap(value)) {
    return value;
  }
  return null;
}

export function normalizeLayerPayload(payload, regionId) {
  const source = extractResponseData(payload) || {};
  const layers = source.layers || {};

  const normalizedLayers = {
    NDVI: toFeatureCollection(layers.NDVI),
    NDMI: toFeatureCollection(layers.NDMI),
    ALERTS: toFeatureCollection(layers.ALERTS),
    FIRMS: toFeatureCollection(layers.FIRMS),
    REPORTS: toFeatureCollection(layers.REPORTS),
    RISK_SCORE: toFeatureCollection(layers.RISK_SCORE)
  };

  CLIMATE_INDICATORS.forEach((indicator) => {
    const valueMap = toValueMap(layers[indicator]);
    if (valueMap) {
      normalizedLayers[indicator] = valueMap;
    }
  });

  return {
    regionId: source.regionId || regionId,
    generatedAt: source.generatedAt || new Date().toISOString(),
    layers: normalizedLayers
  };
}
