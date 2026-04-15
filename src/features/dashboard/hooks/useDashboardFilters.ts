import { useMemo, useState } from 'react';
import type { DashboardFilters, Granularity, IndicatorType } from '../types';

function formatDate(date: Date): string {
  return date.toISOString().split('T')[0];
}

function createInitialFilters(): DashboardFilters {
  const now = new Date();
  const start = new Date(now);
  start.setDate(now.getDate() - 30);

  return {
    regionId: '',
    indicator: 'NDVI',
    from: formatDate(start),
    to: formatDate(now),
    granularity: 'day',
    mapLimit: 200
  };
}

export function useDashboardFilters() {
  const [filters, setFilters] = useState<DashboardFilters>(() => createInitialFilters());

  const actions = useMemo(
    () => ({
      setRegionId: (regionId: string) => setFilters((prev) => ({ ...prev, regionId })),
      setIndicator: (indicator: IndicatorType) => setFilters((prev) => ({ ...prev, indicator })),
      setFrom: (from: string) => setFilters((prev) => ({ ...prev, from })),
      setTo: (to: string) => setFilters((prev) => ({ ...prev, to })),
      setGranularity: (granularity: Granularity) => setFilters((prev) => ({ ...prev, granularity })),
      setMapLimit: (mapLimit: number) =>
        setFilters((prev) => ({
          ...prev,
          mapLimit: Number.isFinite(mapLimit) && mapLimit > 0 ? Math.min(mapLimit, 500) : prev.mapLimit
        })),
      resetFilters: () => setFilters(createInitialFilters())
    }),
    []
  );

  return { filters, ...actions };
}
