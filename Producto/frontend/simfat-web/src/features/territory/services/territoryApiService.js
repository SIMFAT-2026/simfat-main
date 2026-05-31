import axiosClient from '../../../api/axiosClient';

const TERRITORY_ENDPOINTS = {
  layers: '/api/territory/layers',
  bounds: '/api/territory/bounds',
  riskScore: (regionId) => `/api/territory/risk-score/${regionId}`,
  geojson: (regionId) => `/api/territory/geojson/${regionId}`,
  comunalScores: (regionId) => `/api/territory/risk-score/comunas/${regionId}`
};

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

function toBounds(value) {
  if (!Array.isArray(value) || value.length !== 2) {
    return null;
  }

  const first = value[0];
  const second = value[1];

  if (!Array.isArray(first) || !Array.isArray(second) || first.length !== 2 || second.length !== 2) {
    return null;
  }

  const parsed = [
    [Number(first[0]), Number(first[1])],
    [Number(second[0]), Number(second[1])]
  ];

  if (!parsed.every((point) => Number.isFinite(point[0]) && Number.isFinite(point[1]))) {
    return null;
  }

  return parsed;
}

function toCenter(value, fallbackCenter) {
  if (!Array.isArray(value) || value.length !== 2) {
    return fallbackCenter;
  }

  const parsed = [Number(value[0]), Number(value[1])];
  if (Number.isFinite(parsed[0]) && Number.isFinite(parsed[1])) {
    return parsed;
  }
  return fallbackCenter;
}

function extractResponseData(payload) {
  if (payload && typeof payload === 'object' && 'data' in payload) {
    return payload.data;
  }
  return payload;
}

function normalizeLayerPayload(payload, regionId) {
  const source = extractResponseData(payload) || {};
  const layers = source.layers || {};

  return {
    regionId: source.regionId || regionId,
    generatedAt: source.generatedAt || new Date().toISOString(),
    layers: {
      NDVI: toFeatureCollection(layers.NDVI),
      NDMI: toFeatureCollection(layers.NDMI),
      LOSS: toFeatureCollection(layers.LOSS),
      ALERTS: toFeatureCollection(layers.ALERTS),
      FIRMS: toFeatureCollection(layers.FIRMS),
      REPORTS: toFeatureCollection(layers.REPORTS),
      RISK_SCORE: toFeatureCollection(layers.RISK_SCORE)
    }
  };
}

function normalizeBoundsPayload(payload, fallback) {
  const source = extractResponseData(payload) || {};
  const bounds = toBounds(source.bounds) || fallback.bounds;

  return {
    bounds,
    center: toCenter(source.center, fallback.center),
    zoom: Number.isFinite(Number(source.zoom)) ? Number(source.zoom) : fallback.zoom
  };
}

export async function fetchTerritoryLayers({ regionId, indicators, from, to }) {
  const response = await axiosClient.get(TERRITORY_ENDPOINTS.layers, {
    params: {
      regionId,
      indicators: indicators.join(','),
      from,
      to
    }
  });

  return normalizeLayerPayload(response.data, regionId);
}

export async function fetchTerritoryBounds(regionId, fallback) {
  const response = await axiosClient.get(TERRITORY_ENDPOINTS.bounds, {
    params: { regionId }
  });
  return normalizeBoundsPayload(response.data, fallback);
}

export async function fetchTerritoryGeoJson(regionId) {
  const response = await axiosClient.get(TERRITORY_ENDPOINTS.geojson(regionId));
  return response.data;
}

export async function fetchComunalRiskScores(regionId) {
  const response = await axiosClient.get(TERRITORY_ENDPOINTS.comunalScores(regionId));
  const source = (response.data && response.data.data) || response.data || {};
  return source;
}

export async function fetchTerritoryRiskScore(regionId) {
  const response = await axiosClient.get(TERRITORY_ENDPOINTS.riskScore(regionId));
  const source = (response.data && response.data.data) || response.data || {};
  return {
    regionId: source.regionId || regionId,
    scoreComposite: source.scoreComposite ?? null,
    alertLevel: source.alertLevel || 'NORMAL',
    qualityFlag: source.qualityFlag || 'NO_DATA',
    computedAt: source.computedAt || null,
    components: source.components || null
  };
}
