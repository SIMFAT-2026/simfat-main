import { useCallback, useEffect, useMemo, useState } from 'react';
import { fetchTerritoryBounds, fetchTerritoryLayers } from '../services/territoryApiService';

const CACHE_TTL_MS = 120_000;
const pendingByKey = new Map();
const cacheByKey = new Map();

const REGION_CONFIG = {
  biobio: {
    id: 'biobio',
    label: 'Biobio',
    center: [-37.5, -72.5],
    bounds: [
      [-38.9, -74.1],
      [-36.3, -71.0]
    ],
    zoom: 8
  },
  araucania: {
    id: 'araucania',
    label: 'La Araucania',
    center: [-38.7, -72.4],
    bounds: [
      [-39.8, -73.9],
      [-37.8, -71.2]
    ],
    zoom: 8
  }
};

const REGION_OPTIONS = Object.values(REGION_CONFIG);
const DEFAULT_INDICATORS = ['NDVI', 'NDMI', 'LOSS', 'ALERTS', 'FIRMS', 'REPORTS', 'RISK_SCORE'];
const DEFAULT_VISIBLE_INDICATORS = ['RISK_SCORE', 'FIRMS', 'ALERTS', 'REPORTS'];
const REGION_IDS = new Set(REGION_OPTIONS.map((region) => region.id));

function createPointFeature(id, lng, lat, properties) {
  return {
    type: 'Feature',
    id,
    properties,
    geometry: {
      type: 'Point',
      coordinates: [lng, lat]
    }
  };
}

function createMockLayers(regionId) {
  if (regionId === 'araucania') {
    return {
      NDVI: {
        type: 'FeatureCollection',
        features: [
          createPointFeature('ar-ndvi-1', -72.58, -38.74, { label: 'Temuco', indicator: 'NDVI', value: 0.54 }),
          createPointFeature('ar-ndvi-2', -72.35, -38.45, { label: 'Vilcun', indicator: 'NDVI', value: 0.49 })
        ]
      },
      NDMI: {
        type: 'FeatureCollection',
        features: [
          createPointFeature('ar-ndmi-1', -72.62, -38.73, { label: 'Temuco', indicator: 'NDMI', value: 0.31 }),
          createPointFeature('ar-ndmi-2', -71.95, -38.68, { label: 'Curacautin', indicator: 'NDMI', value: 0.28 })
        ]
      },
      LOSS: {
        type: 'FeatureCollection',
        features: [
          createPointFeature('ar-loss-1', -72.75, -38.98, { label: 'Nueva Imperial', indicator: 'LOSS', hectares: 11 }),
          createPointFeature('ar-loss-2', -72.05, -38.95, { label: 'Padre Las Casas', indicator: 'LOSS', hectares: 8 })
        ]
      },
      ALERTS: {
        type: 'FeatureCollection',
        features: [
          createPointFeature('ar-alert-1', -72.62, -38.66, { label: 'Alerta de calor', indicator: 'ALERTS', level: 'ALTO' }),
          createPointFeature('ar-alert-2', -72.18, -38.88, { label: 'Riesgo medio', indicator: 'ALERTS', level: 'MEDIO' })
        ]
      },
      REPORTS: {
        type: 'FeatureCollection',
        features: [
          createPointFeature('ar-report-1', -72.57, -38.7, { label: 'Humo reportado', indicator: 'REPORTS', category: 'HUMO' }),
          createPointFeature('ar-report-2', -72.14, -38.76, {
            label: 'Quema no autorizada',
            indicator: 'REPORTS',
            category: 'QUEMA'
          })
        ]
      },
      FIRMS: { type: 'FeatureCollection', features: [] },
      RISK_SCORE: {
        type: 'FeatureCollection',
        features: [
          createPointFeature('ar-risk', -72.4, -38.7, { label: 'La Araucania', indicator: 'RISK_SCORE', score: 0.0, alertLevel: 'NORMAL' })
        ]
      }
    };
  }

  return {
    NDVI: {
      type: 'FeatureCollection',
      features: [
        createPointFeature('bb-ndvi-1', -73.05, -36.82, { label: 'Concepcion', indicator: 'NDVI', value: 0.57 }),
        createPointFeature('bb-ndvi-2', -72.58, -37.47, { label: 'Los Angeles', indicator: 'NDVI', value: 0.5 })
      ]
    },
    NDMI: {
      type: 'FeatureCollection',
      features: [
        createPointFeature('bb-ndmi-1', -73.12, -36.73, { label: 'Talcahuano', indicator: 'NDMI', value: 0.35 }),
        createPointFeature('bb-ndmi-2', -72.93, -37.08, { label: 'Florida', indicator: 'NDMI', value: 0.29 })
      ]
    },
    LOSS: {
      type: 'FeatureCollection',
      features: [
        createPointFeature('bb-loss-1', -72.9, -37.05, { label: 'Coronel', indicator: 'LOSS', hectares: 9 }),
        createPointFeature('bb-loss-2', -72.42, -37.38, { label: 'Nacimiento', indicator: 'LOSS', hectares: 7 })
      ]
    },
    ALERTS: {
      type: 'FeatureCollection',
      features: [
        createPointFeature('bb-alert-1', -73.1, -36.78, { label: 'Alerta critica', indicator: 'ALERTS', level: 'CRITICO' }),
        createPointFeature('bb-alert-2', -72.74, -37.12, { label: 'Alerta alta', indicator: 'ALERTS', level: 'ALTO' })
      ]
    },
    REPORTS: {
      type: 'FeatureCollection',
      features: [
        createPointFeature('bb-report-1', -73.01, -36.79, { label: 'Foco de humo', indicator: 'REPORTS', category: 'HUMO' }),
        createPointFeature('bb-report-2', -72.66, -37.42, { label: 'Fuego en ladera', indicator: 'REPORTS', category: 'FOCO' })
      ]
    },
    FIRMS: { type: 'FeatureCollection', features: [] },
    RISK_SCORE: {
      type: 'FeatureCollection',
      features: [
        createPointFeature('bb-risk', -72.5, -37.5, { label: 'Biobio', indicator: 'RISK_SCORE', score: 0.0, alertLevel: 'NORMAL' })
      ]
    }
  };
}

function createMockRegionData(regionId, from, to) {
  const region = REGION_CONFIG[regionId] || REGION_CONFIG.biobio;
  return {
    regionId: region.id,
    center: region.center,
    bounds: region.bounds,
    zoom: region.zoom,
    generatedAt: new Date().toISOString(),
    source: 'mock',
    requestedRange: { from, to },
    layers: createMockLayers(region.id)
  };
}

function cacheKey(regionId, indicators, from, to) {
  return `${regionId}|${indicators.join(',')}|${from}|${to}`;
}

async function loadRegionData({ regionId, indicators, from, to, force = false }) {
  const key = cacheKey(regionId, indicators, from, to);
  const cached = cacheByKey.get(key);

  if (!force && cached && Date.now() < cached.expiresAt) {
    return cached.data;
  }

  const existingPromise = pendingByKey.get(key);
  if (!force && existingPromise) {
    return existingPromise;
  }

  const regionFallback = REGION_CONFIG[regionId] || REGION_CONFIG.biobio;
  const requestPromise = (async () => {
    try {
      const [boundsData, layerData] = await Promise.all([
        fetchTerritoryBounds(regionId, regionFallback),
        fetchTerritoryLayers({ regionId, indicators, from, to })
      ]);

      return {
        regionId: layerData.regionId || regionId,
        center: boundsData.center,
        bounds: boundsData.bounds,
        zoom: boundsData.zoom,
        generatedAt: layerData.generatedAt,
        source: 'backend',
        requestedRange: { from, to },
        layers: layerData.layers
      };
    } catch {
      return createMockRegionData(regionId, from, to);
    }
  })();

  pendingByKey.set(key, requestPromise);

  try {
    const data = await requestPromise;
    cacheByKey.set(key, {
      data,
      expiresAt: Date.now() + CACHE_TTL_MS
    });
    return data;
  } finally {
    pendingByKey.delete(key);
  }
}

function defaultDateRange() {
  const now = new Date();
  const from = new Date(now);
  from.setDate(now.getDate() - 3);

  return {
    from: from.toISOString().slice(0, 10),
    to: now.toISOString().slice(0, 10)
  };
}

export function useTerritoryLayers(options = {}) {
  const initialRegionId = REGION_IDS.has(options.initialRegionId) ? options.initialRegionId : 'biobio';
  const initialVisibleIndicators = Array.isArray(options.initialVisibleIndicators) && options.initialVisibleIndicators.length > 0
    ? options.initialVisibleIndicators.filter((indicator) => DEFAULT_INDICATORS.includes(indicator))
    : DEFAULT_VISIBLE_INDICATORS;

  const [selectedRegionId, setSelectedRegionId] = useState(initialRegionId);
  const [visibleIndicators, setVisibleIndicators] = useState(initialVisibleIndicators);
  const [dateRange] = useState(defaultDateRange);
  const [dataByRegion, setDataByRegion] = useState({});
  const [loadingRegionId, setLoadingRegionId] = useState('');
  const [errorByRegion, setErrorByRegion] = useState({});

  const loadRegion = useCallback(
    async (regionId, force = false) => {
      setLoadingRegionId(regionId);
      setErrorByRegion((prev) => ({ ...prev, [regionId]: '' }));

      try {
        const regionData = await loadRegionData({
          regionId,
          indicators: DEFAULT_INDICATORS,
          from: dateRange.from,
          to: dateRange.to,
          force
        });

        setDataByRegion((prev) => ({
          ...prev,
          [regionId]: regionData
        }));
      } catch (error) {
        const message = error instanceof Error ? error.message : 'No fue posible cargar la capa territorial.';
        setErrorByRegion((prev) => ({
          ...prev,
          [regionId]: message
        }));
      } finally {
        setLoadingRegionId((current) => (current === regionId ? '' : current));
      }
    },
    [dateRange.from, dateRange.to]
  );

  useEffect(() => {
    if (REGION_IDS.has(options.initialRegionId)) {
      setSelectedRegionId(options.initialRegionId);
    }
  }, [options.initialRegionId]);

  useEffect(() => {
    if (Array.isArray(options.initialVisibleIndicators) && options.initialVisibleIndicators.length > 0) {
      const normalized = options.initialVisibleIndicators.filter((indicator) => DEFAULT_INDICATORS.includes(indicator));
      if (normalized.length > 0) {
        setVisibleIndicators(normalized);
      }
    }
  }, [options.initialVisibleIndicators]);

  useEffect(() => {
    let mounted = true;

    async function bootstrap() {
      await loadRegion(selectedRegionId, false);
      const remaining = REGION_OPTIONS.filter((region) => region.id !== selectedRegionId);
      for (const region of remaining) {
        if (!mounted) {
          break;
        }
        await loadRegion(region.id, false);
      }
    }

    bootstrap();

    return () => {
      mounted = false;
    };
  }, [loadRegion, selectedRegionId]);

  const toggleIndicator = useCallback((indicator) => {
    setVisibleIndicators((prev) => {
      if (prev.includes(indicator)) {
        return prev.filter((item) => item !== indicator);
      }
      return [...prev, indicator];
    });
  }, []);

  const reloadSelectedRegion = useCallback(async () => {
    await loadRegion(selectedRegionId, true);
  }, [loadRegion, selectedRegionId]);

  const selectedRegionData = dataByRegion[selectedRegionId] || null;
  const selectedRegionError = errorByRegion[selectedRegionId] || '';

  return useMemo(
    () => ({
      regionOptions: REGION_OPTIONS,
      selectedRegionId,
      setSelectedRegionId,
      visibleIndicators,
      toggleIndicator,
      selectedRegionData,
      selectedRegionError,
      loading: loadingRegionId === selectedRegionId && !selectedRegionData,
      refreshing: loadingRegionId === selectedRegionId && Boolean(selectedRegionData),
      reloadSelectedRegion,
      cacheTtlMs: CACHE_TTL_MS
    }),
    [
      selectedRegionData,
      selectedRegionError,
      selectedRegionId,
      visibleIndicators,
      toggleIndicator,
      loadingRegionId,
      reloadSelectedRegion
    ]
  );
}
