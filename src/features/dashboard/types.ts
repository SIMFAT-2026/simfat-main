export type IndicatorType = 'NDVI' | 'NDMI';
export type Granularity = 'day' | 'week' | 'month';
export type CriticityLevel = 'HIGH' | 'MEDIUM' | 'LOW' | 'CRITICAL' | 'UNKNOWN';

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface DashboardSummaryDto {
  totalHectaresLost: number;
  totalAlerts: number;
  worstPeriodLabel: string;
  trendLabel: string;
  monitoredRegions: number;
}

export interface CriticalRegionDto {
  id: string;
  regionId: string;
  regionName: string;
  criticity: CriticityLevel;
  hectaresLost: number;
  totalAlerts: number;
}

export interface LossTrendPointDto {
  label: string;
  hectaresLost: number;
}

export interface AlertsSummaryDto {
  category: string;
  total: number;
  level: CriticityLevel;
}

export interface LatestIndicatorDto {
  regionId: string;
  regionName: string;
  indicator: IndicatorType;
  value: number | null;
  measuredAt: string;
  quality: 'GOOD' | 'WARN' | 'STALE' | 'UNKNOWN';
}

export interface IndicatorSeriesPointDto {
  date: string;
  value: number | null;
}

export interface IndicatorMapPointDto {
  regionId: string;
  regionName: string;
  indicator: IndicatorType;
  value: number | null;
  criticity: CriticityLevel;
}

export interface DataFreshnessDto {
  regionId: string;
  regionName: string;
  lastSyncAt: string;
  isFresh: boolean;
  lagHours: number;
}

export interface SyncRunResultDto {
  startedAt: string;
  status: string;
  regionId: string | null;
  message: string;
}

export interface DashboardFilters {
  regionId: string;
  indicator: IndicatorType;
  from: string;
  to: string;
  granularity: Granularity;
  mapLimit: number;
}
