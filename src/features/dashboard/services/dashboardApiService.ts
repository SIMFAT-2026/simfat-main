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
  SyncRunResultDto
} from '../types';

interface RequestParams {
  [key: string]: string | number | boolean | undefined;
}

const DASHBOARD_ENDPOINTS = {
  summary: '/api/dashboard/summary',
  criticalRegions: '/api/dashboard/critical-regions',
  lossTrend: '/api/dashboard/loss-trend',
  alertsSummary: '/api/dashboard/alerts-summary',
  latestIndicators: '/api/dashboard/indicators/latest',
  indicatorSeries: '/api/dashboard/indicators/series',
  indicatorMap: '/api/dashboard/indicators/map',
  dataFreshness: '/api/dashboard/data-freshness',
  syncRun: '/api/dashboard/sync/run'
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
  if (!isObject(payload) || !('success' in payload) || !('data' in payload)) {
    throw new Error('Formato inesperado de respuesta del backend');
  }

  return payload as ApiResponse<T>;
}

function handleRequestError(error: unknown, endpoint: string, params?: RequestParams): never {
  const normalizedError = toApiError(error);
  console.error('[dashboard-api] Error tecnico', {
    endpoint,
    params,
    status: normalizedError.status,
    path: normalizedError.path,
    raw: normalizedError.raw
  });
  throw new Error(normalizedError.message || 'No fue posible cargar datos del dashboard.');
}

async function getApiData<T>(endpoint: string, params?: RequestParams): Promise<T> {
  try {
    const response = await axiosClient.get<ApiResponse<T>>(endpoint, { params });
    const payload = ensureApiResponse<T>(response.data);

    if (!payload.success) {
      throw new Error(payload.message || 'La API respondio con success=false');
    }

    return payload.data;
  } catch (error) {
    return handleRequestError(error, endpoint, params);
  }
}

async function postApiData<T>(endpoint: string, params?: RequestParams): Promise<T> {
  try {
    const response = await axiosClient.post<ApiResponse<T>>(endpoint, null, { params });
    const payload = ensureApiResponse<T>(response.data);

    if (!payload.success) {
      throw new Error(payload.message || 'La API respondio con success=false');
    }

    return payload.data;
  } catch (error) {
    return handleRequestError(error, endpoint, params);
  }
}

function normalizeSummary(payload: unknown): DashboardSummaryDto {
  const source = isObject(payload) ? payload : {};
  return {
    totalHectaresLost: toNumber(source.totalHectaresLost ?? source.totalHectareasPerdidas),
    totalAlerts: toNumber(source.totalAlerts ?? source.totalAlertas),
    worstPeriodLabel: toString(source.worstPeriodLabel ?? source.anioMayorPerdida, '-'),
    trendLabel: toString(source.trendLabel ?? source.tendenciaGeneral, '-'),
    monitoredRegions: toNumber(source.monitoredRegions ?? source.totalRegiones)
  };
}

function normalizeCriticalRegion(payload: unknown, index: number): CriticalRegionDto {
  const source = isObject(payload) ? payload : {};
  const regionId = toString(source.regionId ?? source.id, `region-${index + 1}`);
  const regionName = toString(source.regionName ?? source.nombre ?? source.region, `Region ${index + 1}`);

  return {
    id: toString(source.id, regionId),
    regionId,
    regionName,
    criticity: toCriticity(source.criticity ?? source.nivelCriticidad ?? source.level),
    hectaresLost: toNumber(source.hectaresLost ?? source.hectareasPerdidas),
    totalAlerts: toNumber(source.totalAlerts ?? source.totalAlertas)
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

function normalizeLatestIndicator(payload: unknown, indicator: IndicatorType, regionId: string): LatestIndicatorDto {
  const source = isObject(payload) ? payload : {};
  return {
    regionId: toString(source.regionId, regionId),
    regionName: toString(source.regionName ?? source.nombreRegion, regionId || 'Region'),
    indicator,
    value: toNullableNumber(source.value ?? source.valor),
    measuredAt: toString(source.measuredAt ?? source.timestamp ?? source.fecha, ''),
    quality: toString(source.quality ?? source.calidad, 'UNKNOWN').toUpperCase() as LatestIndicatorDto['quality']
  };
}

function normalizeSeriesPoint(payload: unknown, index: number): IndicatorSeriesPointDto {
  const source = isObject(payload) ? payload : {};
  return {
    date: toString(source.date ?? source.fecha ?? source.label, `P-${index + 1}`),
    value: toNullableNumber(source.value ?? source.valor)
  };
}

function normalizeMapPoint(payload: unknown, indicator: IndicatorType, index: number): IndicatorMapPointDto {
  const source = isObject(payload) ? payload : {};
  const regionId = toString(source.regionId ?? source.id, `region-${index + 1}`);
  return {
    regionId,
    regionName: toString(source.regionName ?? source.nombreRegion ?? source.region, `Region ${index + 1}`),
    indicator,
    value: toNullableNumber(source.value ?? source.valor),
    criticity: toCriticity(source.criticity ?? source.level)
  };
}

function normalizeDataFreshness(payload: unknown, regionId: string): DataFreshnessDto {
  const source = isObject(payload) ? payload : {};
  return {
    regionId: toString(source.regionId, regionId),
    regionName: toString(source.regionName ?? source.nombreRegion, regionId || 'Region'),
    lastSyncAt: toString(source.lastSyncAt ?? source.ultimaSincronizacion, ''),
    isFresh: Boolean(source.isFresh ?? source.fresco),
    lagHours: toNumber(source.lagHours ?? source.horasDeAtraso)
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
  const payload = await getApiData<unknown>(DASHBOARD_ENDPOINTS.summary);
  return normalizeSummary(payload);
}

export async function fetchCriticalRegions(): Promise<CriticalRegionDto[]> {
  const payload = await getApiData<unknown[]>(DASHBOARD_ENDPOINTS.criticalRegions);
  return Array.isArray(payload) ? payload.map(normalizeCriticalRegion) : [];
}

export async function fetchLossTrend(): Promise<LossTrendPointDto[]> {
  const payload = await getApiData<unknown[]>(DASHBOARD_ENDPOINTS.lossTrend);
  return Array.isArray(payload) ? payload.map(normalizeLossTrend) : [];
}

export async function fetchAlertsSummary(): Promise<AlertsSummaryDto[]> {
  const payload = await getApiData<unknown[]>(DASHBOARD_ENDPOINTS.alertsSummary);
  return Array.isArray(payload) ? payload.map(normalizeAlertsSummary) : [];
}

export async function fetchLatestIndicator(regionId: string, indicator: IndicatorType): Promise<LatestIndicatorDto> {
  const payload = await getApiData<unknown>(DASHBOARD_ENDPOINTS.latestIndicators, { regionId, indicator });
  return normalizeLatestIndicator(payload, indicator, regionId);
}

export async function fetchIndicatorSeries(params: {
  regionId: string;
  indicator: IndicatorType;
  from: string;
  to: string;
  granularity: Granularity;
}): Promise<IndicatorSeriesPointDto[]> {
  const payload = await getApiData<unknown[]>(DASHBOARD_ENDPOINTS.indicatorSeries, params);
  return Array.isArray(payload) ? payload.map(normalizeSeriesPoint) : [];
}

export async function fetchIndicatorMap(params: {
  indicator: IndicatorType;
  from: string;
  to: string;
  limit: number;
}): Promise<IndicatorMapPointDto[]> {
  const payload = await getApiData<unknown[]>(DASHBOARD_ENDPOINTS.indicatorMap, params);
  return Array.isArray(payload) ? payload.map((item, index) => normalizeMapPoint(item, params.indicator, index)) : [];
}

export async function fetchDataFreshness(regionId: string): Promise<DataFreshnessDto> {
  const payload = await getApiData<unknown>(DASHBOARD_ENDPOINTS.dataFreshness, { regionId });
  return normalizeDataFreshness(payload, regionId);
}

export async function triggerDashboardSync(regionId?: string): Promise<SyncRunResultDto> {
  const payload = await postApiData<unknown>(DASHBOARD_ENDPOINTS.syncRun, regionId ? { regionId } : undefined);
  return normalizeSyncRun(payload, regionId);
}
