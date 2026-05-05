import { useCallback, useMemo } from 'react';
import {
  fetchAlertsSummary,
  fetchCriticalRegions,
  fetchDashboardSummary,
  fetchDataFreshness,
  fetchIndicatorMap,
  fetchIndicatorSeries,
  fetchLatestIndicator,
  fetchLossTrend,
  fetchRegions,
  triggerDashboardSync
} from '../services/dashboardApiService';
import type { DashboardFilters } from '../types';
import { useDashboardResource } from './useDashboardResource';

export function useDashboardData(filters: DashboardFilters) {
  const hasRegion = Boolean(filters.regionId);
  const hasDateRange = Boolean(filters.from && filters.to);

  const regions = useDashboardResource({
    cacheKey: 'dashboard:regions',
    fetcher: fetchRegions,
    ttlMs: 120_000
  });

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
    ttlMs: 60_000
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
    ttlMs: 120_000
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
    ttlMs: 60_000
  });

  const syncNow = useCallback(async () => {
    return triggerDashboardSync(filters.regionId || undefined, filters.from || undefined, filters.to || undefined);
  }, [filters.from, filters.regionId, filters.to]);

  const reloadAll = useCallback(async () => {
    await Promise.all([
      summary.reload(),
      criticalRegions.reload(),
      lossTrend.reload(),
      alertsSummary.reload(),
      regions.reload(),
      hasRegion ? latestIndicator.reload() : Promise.resolve(),
      hasRegion && hasDateRange ? indicatorSeries.reload() : Promise.resolve(),
      hasDateRange ? indicatorMap.reload() : Promise.resolve(),
      hasRegion ? dataFreshness.reload() : Promise.resolve()
    ]);
  }, [
    alertsSummary,
    criticalRegions,
    dataFreshness,
    hasDateRange,
    hasRegion,
    indicatorMap,
    indicatorSeries,
    latestIndicator,
    lossTrend,
    regions,
    summary
  ]);

  return useMemo(
    () => ({
      summary,
      regions,
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
      regions,
      reloadAll,
      summary,
      syncNow
    ]
  );
}
