import axiosClient from '../../../api/axiosClient';
import publicAxiosClient from '../../../api/publicAxiosClient';
import { extractResponseData, normalizeLayerPayload } from '../utils/layerPayload';
import { buildMapbiomasParams } from '../utils/mapbiomasLayer';

const TERRITORY_ENDPOINTS = {
  layers: '/api/territory/layers',
  bounds: '/api/territory/bounds',
  riskScore: (regionId) => `/api/territory/risk-score/${regionId}`,
  geojson: (regionId) => `/api/territory/geojson/${regionId}`,
  comunalScores: (regionId) => `/api/territory/risk-score/comunas/${regionId}`,
  comunaHistory: (gadmGid, days) => `/api/territory/risk-score/comunas/${gadmGid}/history?days=${days}`,
  copernicusSync: (comunaId) => `/api/territory/risk-score/comunas/${comunaId}/copernicus-sync`
};

// Sin login (modo publico de /territorio) — mismo shape de respuesta que
// /bounds y /layers, salvo REPORTS (anonimizado en el backend).
const PUBLIC_TERRITORY_ENDPOINTS = {
  layers: '/api/territory/public/layers',
  bounds: '/api/territory/public/bounds'
};

// Anonymous mode must never touch the shared client (Bearer + 401 refresh).
function clientFor(publicMode) {
  return publicMode ? publicAxiosClient : axiosClient;
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

// Fetches ONE yearly MapBiomas layer (BURNED or LANDCOVER). These are not part
// of the region bulk load: each year is requested on demand. When year is
// omitted the backend applies its data-derived default year. Resolves to the
// layer payload (year, minYear, maxYear, citation, values...) or null.
export async function fetchMapbiomasLayer(indicator, { regionId, year, publicMode = false, signal } = {}) {
  const params = buildMapbiomasParams({ regionId, indicator, year });
  const endpoint = publicMode ? PUBLIC_TERRITORY_ENDPOINTS.layers : TERRITORY_ENDPOINTS.layers;
  const response = await clientFor(publicMode).get(endpoint, { params, signal });
  return normalizeLayerPayload(response.data, regionId).layers[indicator] ?? null;
}

// Per-comuna MapBiomas detail for the comuna panel. Always the tokenless public
// client (anonymous-readable route). A missing year lets the backend pick its
// default. Rejects with an ApiError carrying `status` (e.g. 404 unknown comuna).
export async function fetchComunaMapbiomas(comunaId, { year, signal } = {}) {
  const params = year === undefined || year === null ? {} : { year };
  const response = await publicAxiosClient.get(
    `/api/territory/public/mapbiomas/${encodeURIComponent(comunaId)}`,
    { params, signal }
  );
  return extractResponseData(response.data);
}

export async function fetchTerritoryBounds(regionId, fallback) {
  const response = await axiosClient.get(TERRITORY_ENDPOINTS.bounds, {
    params: { regionId }
  });
  return normalizeBoundsPayload(response.data, fallback);
}

export async function fetchPublicTerritoryLayers({ regionId, indicators, from, to }) {
  const response = await publicAxiosClient.get(PUBLIC_TERRITORY_ENDPOINTS.layers, {
    params: {
      regionId,
      indicators: indicators.join(','),
      from,
      to
    }
  });

  return normalizeLayerPayload(response.data, regionId);
}

export async function fetchPublicTerritoryBounds(regionId, fallback) {
  const response = await publicAxiosClient.get(PUBLIC_TERRITORY_ENDPOINTS.bounds, {
    params: { regionId }
  });
  return normalizeBoundsPayload(response.data, fallback);
}

export async function fetchTerritoryGeoJson(regionId, { publicMode = false } = {}) {
  // Served as static asset by Spring Boot — no auth needed, cached 24h
  const response = await clientFor(publicMode).get(`/geojson/comunas-${regionId.toLowerCase()}.geojson`);
  return response.data;
}

export async function fetchComunalRiskScores(regionId, { publicMode = false } = {}) {
  const response = await clientFor(publicMode).get(TERRITORY_ENDPOINTS.comunalScores(regionId));
  const source = (response.data && response.data.data) || response.data || {};
  return source;
}

export async function fetchComunaHistory(gadmGid, days = 7, { publicMode = false } = {}) {
  const response = await clientFor(publicMode).get(TERRITORY_ENDPOINTS.comunaHistory(gadmGid, days));
  const source = (response.data && response.data.data) || response.data || {};
  return source;
}

export async function syncComunaCopernicus(comunaId) {
  const response = await axiosClient.post(TERRITORY_ENDPOINTS.copernicusSync(comunaId));
  const source = (response.data && response.data.data) || response.data || {};
  return source;
}

export async function fetchTerritoryRiskScore(regionId, { publicMode = false } = {}) {
  const response = await clientFor(publicMode).get(TERRITORY_ENDPOINTS.riskScore(regionId));
  const source = (response.data && response.data.data) || response.data || {};
  return {
    regionId: source.regionId || regionId,
    scoreComposite: source.scoreComposite ?? null,
    alertLevel: source.alertLevel || 'NORMAL',
    qualityFlag: source.qualityFlag || 'NO_DATA',
    computedAt: source.computedAt || null,
    components: source.components || null,
    fwiInputs: source.fwiInputs || null,
    ndviRaw: source.components?.ndvi?.rawValue ?? null,
    ndmiRaw: source.components?.ndmi?.rawValue ?? null
  };
}
