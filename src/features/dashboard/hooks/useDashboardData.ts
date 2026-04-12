import { useMemo } from 'react';
import {
  fetchAlertsSummary,
  fetchCriticalRegions,
  fetchDashboardSummary,
  fetchDataFreshness,
  fetchIndicatorMap,
  fetchIndicatorSeries,
  fetchLatestIndicator,
  fetchLossTrend,
  triggerDashboardSync
} from '../services/dashboardApiService';
import type { DashboardFilters } from '../types';
import { useDashboardResource } from './useDashboardResource';

export function useDashboardData(filters: DashboardFilters) {
  const hasRegion = Boolean(filters.regionId);
  const hasDateRange = Boolean(filters.from && filters.to);

  const summary = useDashboardResource({
    cacheKey: 'dashboard:summary',
    fetcher: fetchDashboardSummary,
    ttlMs: 45_000
  });

  const criticalRegions = useDashboardResource({
    cacheKey: 'dashboard:critical-regions',
    fetcher: fetchCriticalRegions,
    ttlMs: 45_000
  });

  const lossTrend = useDashboardResource({
    cacheKey: 'dashboard:loss-trend',
    fetcher: fetchLossTrend,
    ttlMs: 45_000
  });

  const alertsSummary = useDashboardResource({
    cacheKey: 'dashboard:alerts-summary',
    fetcher: fetchAlertsSummary,
    ttlMs: 45_000
  });

  const latestIndicator = useDashboardResource({
    cacheKey: `dashboard:indicator-latest:${filters.regionId}:${filters.indicator}`,
    enabled: hasRegion,
    fetcher: () => fetchLatestIndicator(filters.regionId, filters.indicator),
    ttlMs: 30_000
  });

  const indicatorSeries = useDashboardResource({
    cacheKey: `dashboard:indicator-series:${filters.regionId}:${filters.indicator}:${filters.from}:${filters.to}:${filters.granularity}`,
    enabled: hasRegion && hasDateRange,
    fetcher: () =>
      fetchIndicatorSeries({
        regionId: filters.regionId,
        indicator: filters.indicator,
        from: filters.from,
        to: filters.to,
        granularity: filters.granularity
      }),
    ttlMs: 30_000
  });

  const indicatorMap = useDashboardResource({
    cacheKey: `dashboard:indicator-map:${filters.indicator}:${filters.from}:${filters.to}:${filters.mapLimit}`,
    enabled: hasDateRange,
    fetcher: () =>
      fetchIndicatorMap({
        indicator: filters.indicator,
        from: filters.from,
        to: filters.to,
        limit: filters.mapLimit
      }),
    ttlMs: 30_000
  });

  const dataFreshness = useDashboardResource({
    cacheKey: `dashboard:data-freshness:${filters.regionId}`,
    enabled: hasRegion,
    fetcher: () => fetchDataFreshness(filters.regionId),
    ttlMs: 20_000
  });

  const syncNow = async () => {
    return triggerDashboardSync(filters.regionId || undefined);
  };

  const reloadAll = async () => {
    await Promise.all([
      summary.reload(),
      criticalRegions.reload(),
      lossTrend.reload(),
      alertsSummary.reload(),
      hasRegion ? latestIndicator.reload() : Promise.resolve(),
      hasRegion && hasDateRange ? indicatorSeries.reload() : Promise.resolve(),
      hasDateRange ? indicatorMap.reload() : Promise.resolve(),
      hasRegion ? dataFreshness.reload() : Promise.resolve()
    ]);
  };

  return useMemo(
    () => ({
      summary,
      criticalRegions,
      lossTrend,
      alertsSummary,
      latestIndicator,
      indicatorSeries,
      indicatorMap,
      dataFreshness,
      hasRegion,
      hasDateRange,
      syncNow,
      reloadAll
    }),
    [
      alertsSummary,
      criticalRegions,
      dataFreshness,
      hasDateRange,
      hasRegion,
      indicatorMap,
      indicatorSeries,
      latestIndicator,
      lossTrend,
      summary
    ]
  );
}
