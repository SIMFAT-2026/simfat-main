import axiosClient from '../../../api/axiosClient';
import { toApiError } from '../../../api/apiError';
import type {
  AlertsSummaryDto,
  ApiResponse,
  CriticalRegionDto,
  DataFreshnessDto,
  DashboardSummaryDto,
  Granularity,
  IndicatorMapPointDto,
  IndicatorSeriesPointDto,
  IndicatorType,
  LatestIndicatorDto,
  LossTrendPointDto,
  RegionDto,
  SyncRunResultDto
} from '../types';

interface RequestParams {
  [key: string]: string | number | boolean | undefined;
}

const DASHBOARD_ENDPOINTS = {
  regions: '/api/regions',
  summary: '/api/dashboard/summary',
  criticalRegions: '/api/dashboard/critical-regions',
  lossTrend: '/api/dashboard/loss-trend',
  alertsSummary: '/api/dashboard/alerts-summary',
  latestIndicators: '/api/dashboard/indicators/latest',
  indicatorSeries: '/api/dashboard/indicators/series',
  indicatorMap: '/api/dashboard/indicators/map',
  dataFreshness: '/api/dashboard/data-freshness',
  syncRun: '/api/dashboard/sync/run',
  // ADMIN-only: FIRMS + FWI (clima/viento) por region + recompute comunal
  // en background. Antes solo se podia disparar via curl con un token
  // (no tenia consumidor en la UI) — ver WeatherSyncButton.
  weatherSync: '/api/territory/sync'
};

function isObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object';
}

function toNumber(value: unknown, fallback = 0): number {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }

  if (typeof value === 'string') {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }

  return fallback;
}

function toString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}

function toCriticity(value: unknown): CriticalRegionDto['criticity'] {
  const normalized = toString(value, '').toUpperCase();
  if (normalized === 'HIGH' || normalized === 'ALTO') {
    return 'HIGH';
  }
  if (normalized === 'MEDIUM' || normalized === 'MEDIO') {
    return 'MEDIUM';
  }
  if (normalized === 'LOW' || normalized === 'BAJO') {
    return 'LOW';
  }
  if (normalized === 'CRITICAL' || normalized === 'CRITICO') {
    return 'CRITICAL';
  }
  return 'UNKNOWN';
}

function toNullableNumber(value: unknown): number | null {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const parsed = toNumber(value, Number.NaN);
  return Number.isFinite(parsed) ? parsed : null;
}

function ensureApiResponse<T>(payload: unknown): ApiResponse<T> {
  if (isObject(payload) && 'success' in payload && 'data' in payload) {
    return payload as ApiResponse<T>;
  }

  return {
    success: true,
    message: '',
    data: payload as T,
    timestamp: new Date().toISOString()
  };
}

function handleRequestError(error: unknown, endpoint: string, userMessage: string, params?: RequestParams): never {
  const normalizedError = toApiError(error);
  console.error('[dashboard-api] Error tecnico', {
    endpoint,
    params,
    status: normalizedError.status,
    path: normalizedError.path,
    raw: normalizedError.raw
  });
  throw new Error(normalizedError.message || userMessage);
}

async function getApiData<T>(endpoint: string, userMessage: string, params?: RequestParams): Promise<T> {
  try {
    const response = await axiosClient.get<ApiResponse<T> | T>(endpoint, { params });
    const payload = ensureApiResponse<T>(response.data);

    if (!payload.success) {
      throw new Error(payload.message || 'La API respondio con success=false');
    }

    return payload.data;
  } catch (error) {
    return handleRequestError(error, endpoint, userMessage, params);
  }
}

async function postApiData<T>(endpoint: string, userMessage: string, params?: RequestParams): Promise<T> {
  try {
    const response = await axiosClient.post<ApiResponse<T> | T>(endpoint, null, { params });
    const payload = ensureApiResponse<T>(response.data);

    if (!payload.success) {
      throw new Error(payload.message || 'La API respondio con success=false');
    }

    return payload.data;
  } catch (error) {
    return handleRequestError(error, endpoint, userMessage, params);
  }
}

async function postApiDataWithTimeout<T>(
  endpoint: string,
  userMessage: string,
  timeoutMs: number,
  params?: RequestParams
): Promise<T> {
  try {
    const response = await axiosClient.post<ApiResponse<T> | T>(endpoint, null, {
      params,
      timeout: timeoutMs
    });
    const payload = ensureApiResponse<T>(response.data);

    if (!payload.success) {
      throw new Error(payload.message || 'La API respondio con success=false');
    }

    return payload.data;
  } catch (error) {
    return handleRequestError(error, endpoint, userMessage, params);
  }
}

function normalizeRegion(payload: unknown, index: number): RegionDto {
  const source = isObject(payload) ? payload : {};
  return {
    id: toString(source.id, `region-${index + 1}`),
    nombre: toString(source.nombre ?? source.regionName, `Region ${index + 1}`),
    codigo: toString(source.codigo ?? source.code, '-'),
    zona: toString(source.zona ?? source.zone, ''),
    hectareasBosqueReferencia: toNullableNumber(source.hectareasBosqueReferencia ?? source.referenceHectares)
  };
}

function normalizeSummary(payload: unknown): DashboardSummaryDto {
  const source = isObject(payload) ? payload : {};
  return {
    totalHectaresLost: toNumber(source.totalHectaresLost ?? source.totalHectareasPerdidas),
    totalAlerts: toNumber(source.totalAlerts ?? source.totalAlertas),
    worstPeriodLabel: toString(source.worstPeriodLabel ?? source.anioMayorPerdida, '-'),
    trendLabel: toString(source.trendLabel ?? source.tendenciaGeneral, '-'),
    monitoredRegions: toNumber(source.monitoredRegions ?? source.totalRegiones ?? source.regionesCriticas)
  };
}

function normalizeCriticalRegion(payload: unknown, index: number): CriticalRegionDto {
  const source = isObject(payload) ? payload : {};
  const regionId = toString(source.regionId ?? source.id, `region-${index + 1}`);
  const regionName = toString(
    source.regionName ?? source.nombreRegion ?? source.nombre ?? source.region,
    `Region ${index + 1}`
  );

  return {
    id: toString(source.id, regionId),
    regionId,
    regionName,
    criticity: toCriticity(source.criticity ?? source.nivelCriticidad ?? source.estadoCriticidad ?? source.level),
    hectaresLost: toNumber(source.hectaresLost ?? source.hectareasPerdidas ?? source.porcentajePerdidaActual),
    totalAlerts: toNumber(source.totalAlerts ?? source.totalAlertas ?? source.eventosCalorRecientes)
  };
}

function normalizeLossTrend(payload: unknown, index: number): LossTrendPointDto {
  const source = isObject(payload) ? payload : {};
  return {
    label: toString(source.label ?? source.period ?? source.date ?? source.anio, `P-${index + 1}`),
    hectaresLost: toNumber(source.hectaresLost ?? source.hectareasPerdidas ?? source.value)
  };
}

function normalizeAlertsSummary(payload: unknown, index: number): AlertsSummaryDto {
  const source = isObject(payload) ? payload : {};
  return {
    category: toString(source.category ?? source.categoria, `Categoria ${index + 1}`),
    total: toNumber(source.total ?? source.count),
    level: toCriticity(source.level ?? source.criticidad)
  };
}

function normalizeAlertsSummaryCollection(payload: unknown): AlertsSummaryDto[] {
  if (Array.isArray(payload)) {
    return payload.map(normalizeAlertsSummary);
  }

  if (!isObject(payload)) {
    return [];
  }

  return [
    { category: 'LOW', total: toNumber(payload.bajo), level: 'LOW' },
    { category: 'MEDIUM', total: toNumber(payload.medio), level: 'MEDIUM' },
    { category: 'HIGH', total: toNumber(payload.alto), level: 'HIGH' },
    { category: 'CRITICAL', total: toNumber(payload.critico), level: 'CRITICAL' }
  ];
}

function normalizeLatestIndicator(payload: unknown, indicator: IndicatorType, regionId: string): LatestIndicatorDto {
  const source = isObject(payload) ? payload : {};
  return {
    regionId: toString(source.regionId, regionId),
    indicator: toString(source.indicator, indicator).toUpperCase() as IndicatorType,
    value: toNullableNumber(source.value ?? source.valor),
    observedAt: toString(source.observedAt ?? source.measuredAt ?? source.timestamp ?? source.fecha, ''),
    source: toString(source.source, 'Copernicus/openEO'),
    cached: Boolean(source.cached ?? false)
  };
}

function normalizeSeriesPoint(payload: unknown, index: number): IndicatorSeriesPointDto {
  const source = isObject(payload) ? payload : {};
  return {
    ts: toString(source.ts ?? source.date ?? source.fecha ?? source.label, `P-${index + 1}`),
    value: toNullableNumber(source.value ?? source.valor)
  };
}

function normalizeMapPoint(payload: unknown, indicator: IndicatorType, index: number): IndicatorMapPointDto {
  const source = isObject(payload) ? payload : {};
  const regionId = toString(source.regionId ?? source.id, `region-${index + 1}`);
  const value = toNullableNumber(source.value ?? source.valor);
  const explicitCriticity = toCriticity(source.criticity ?? source.level);
  const inferredCriticity =
    value === null ? 'UNKNOWN' : value >= 0.5 ? 'LOW' : value >= 0.2 ? 'MEDIUM' : 'HIGH';

  return {
    regionId,
    regionName: toString(source.regionName ?? source.nombreRegion ?? source.region ?? source.aoi, `Region ${index + 1}`),
    indicator,
    value,
    criticity: explicitCriticity === 'UNKNOWN' ? inferredCriticity : explicitCriticity
  };
}

function normalizeDataFreshness(payload: unknown, regionId: string): DataFreshnessDto {
  const source = isObject(payload) ? payload : {};
  const rawStatus = toString(source.status, '').toUpperCase();
  const ageSecondsFromLegacyHours =
    source.lagHours !== undefined && source.ageSeconds === undefined ? toNumber(source.lagHours, 0) * 3600 : null;
  const status: DataFreshnessDto['status'] =
    rawStatus === 'FRESH' || rawStatus === 'STALE' || rawStatus === 'EMPTY'
      ? rawStatus
      : Boolean(source.isFresh ?? source.fresco)
        ? 'FRESH'
        : 'STALE';

  return {
    regionId: toString(source.regionId, regionId),
    lastUpdate: toString(source.lastUpdate ?? source.lastSyncAt ?? source.ultimaSincronizacion, ''),
    ageSeconds: toNumber(source.ageSeconds ?? source.age ?? ageSecondsFromLegacyHours, 0),
    status
  };
}

function normalizeSyncRun(payload: unknown, regionId?: string): SyncRunResultDto {
  const source = isObject(payload) ? payload : {};
  return {
    startedAt: toString(source.startedAt ?? source.timestamp, ''),
    status: toString(source.status, 'UNKNOWN'),
    regionId: toString(source.regionId, regionId || '') || null,
    message: toString(source.message, 'Sincronizacion iniciada.')
  };
}

export async function fetchDashboardSummary(): Promise<DashboardSummaryDto> {
  const payload = await getApiData<unknown>(
    DASHBOARD_ENDPOINTS.summary,
    'No fue posible cargar el resumen del dashboard.'
  );
  return normalizeSummary(payload);
}

export async function fetchCriticalRegions(): Promise<CriticalRegionDto[]> {
  const payload = await getApiData<unknown[]>(
    DASHBOARD_ENDPOINTS.criticalRegions,
    'No fue posible cargar las regiones criticas.'
  );
  return Array.isArray(payload) ? payload.map(normalizeCriticalRegion) : [];
}

export async function fetchLossTrend(): Promise<LossTrendPointDto[]> {
  const payload = await getApiData<unknown[]>(
    DASHBOARD_ENDPOINTS.lossTrend,
    'No fue posible cargar la tendencia de perdida.'
  );
  return Array.isArray(payload) ? payload.map(normalizeLossTrend) : [];
}

export async function fetchAlertsSummary(): Promise<AlertsSummaryDto[]> {
  const payload = await getApiData<unknown>(
    DASHBOARD_ENDPOINTS.alertsSummary,
    'No fue posible cargar el resumen de alertas.'
  );
  return normalizeAlertsSummaryCollection(payload);
}

export async function fetchLatestIndicator(regionId: string, indicator: IndicatorType): Promise<LatestIndicatorDto> {
  const payload = await getApiData<unknown>(
    DASHBOARD_ENDPOINTS.latestIndicators,
    'No fue posible cargar el ultimo indicador de la region.',
    { regionId, indicator }
  );
  return normalizeLatestIndicator(payload, indicator, regionId);
}

export async function fetchIndicatorSeries(params: {
  regionId: string;
  indicator: IndicatorType;
  from: string;
  to: string;
  granularity: Granularity;
}): Promise<IndicatorSeriesPointDto[]> {
  const payload = await getApiData<unknown>(
    DASHBOARD_ENDPOINTS.indicatorSeries,
    'No fue posible cargar la serie temporal.',
    params
  );
  const points = Array.isArray(payload)
    ? payload
    : isObject(payload) && Array.isArray(payload.points)
      ? payload.points
      : [];

  return points.map(normalizeSeriesPoint);
}

export async function fetchIndicatorMap(params: {
  indicator: IndicatorType;
  from: string;
  to: string;
  limit: number;
}): Promise<IndicatorMapPointDto[]> {
  const payload = await getApiData<unknown>(
    DASHBOARD_ENDPOINTS.indicatorMap,
    'No fue posible cargar la capa de mapa.',
    params
  );
  const items = Array.isArray(payload)
    ? payload
    : isObject(payload) && Array.isArray(payload.items)
      ? payload.items
      : [];

  return items.map((item, index) => normalizeMapPoint(item, params.indicator, index));
}

export async function fetchDataFreshness(regionId: string): Promise<DataFreshnessDto> {
  const payload = await getApiData<unknown>(
    DASHBOARD_ENDPOINTS.dataFreshness,
    'No fue posible consultar la frescura de datos.',
    { regionId }
  );
  return normalizeDataFreshness(payload, regionId);
}

export async function triggerDashboardSync(regionId?: string, from?: string, to?: string): Promise<SyncRunResultDto> {
  const params: RequestParams = {};
  if (regionId) {
    params.regionId = regionId;
  }
  if (from) {
    params.from = from;
  }
  if (to) {
    params.to = to;
  }

  const payload = await postApiDataWithTimeout<unknown>(
    DASHBOARD_ENDPOINTS.syncRun,
    'No fue posible iniciar la sincronizacion.',
    120000,
    Object.keys(params).length > 0 ? params : undefined
  );
  return normalizeSyncRun(payload, regionId);
}

export async function triggerWeatherSync(regionId: string): Promise<SyncRunResultDto> {
  const payload = await postApiDataWithTimeout<unknown>(
    DASHBOARD_ENDPOINTS.weatherSync,
    'No fue posible iniciar la sincronizacion de clima.',
    30000,
    { regionId }
  );
  return normalizeSyncRun(payload, regionId);
}

export async function fetchRegions(): Promise<RegionDto[]> {
  const payload = await getApiData<unknown[]>(
    DASHBOARD_ENDPOINTS.regions,
    'No fue posible cargar regiones desde backend.'
  );
  return Array.isArray(payload) ? payload.map(normalizeRegion) : [];
}
