import { useEffect } from 'react';
import SectionTitle from '../components/SectionTitle';
import AlertsBar from '../features/dashboard/components/AlertsBar';
import AlertsDonut from '../features/dashboard/components/AlertsDonut';
import CriticalRegionsTable from '../features/dashboard/components/CriticalRegionsTable';
import DashboardFiltersPanel from '../features/dashboard/components/DashboardFilters';
import DashboardKpiCards from '../features/dashboard/components/DashboardKpiCards';
import DataFreshnessChip from '../features/dashboard/components/DataFreshnessChip';
import IndicatorsMapLayer from '../features/dashboard/components/IndicatorsMapLayer';
import IndicatorSeriesChart from '../features/dashboard/components/IndicatorSeriesChart';
import LatestIndicatorsPanel from '../features/dashboard/components/LatestIndicatorsPanel';
import LossTrendChart from '../features/dashboard/components/LossTrendChart';
import SyncNowButton from '../features/dashboard/components/SyncNowButton';
import { useDashboardData } from '../features/dashboard/hooks/useDashboardData';
import { useDashboardFilters } from '../features/dashboard/hooks/useDashboardFilters';
import '../features/dashboard/dashboard.css';

function DashboardPage() {
  const {
    filters,
    setRegionId,
    setIndicator,
    setFrom,
    setTo,
    setGranularity,
    setMapLimit,
    resetFilters
  } = useDashboardFilters();

  const dashboard = useDashboardData(filters);
  const regions = dashboard.regions.data || [];
  const selectedRegion = regions.find((region) => region.id === filters.regionId) || null;

  useEffect(() => {
    if (!filters.regionId && regions.length > 0 && !dashboard.regions.loading) {
      setRegionId(regions[0].id);
    }
  }, [dashboard.regions.loading, filters.regionId, regions, setRegionId]);

  return (
    <section className="page-container">
      <SectionTitle title="Dashboard MVP" subtitle="Monitoreo integrado con Simfat-backend (openEO/Copernicus)" />

      <DashboardFiltersPanel
        filters={filters}
        regions={regions}
        regionsLoading={dashboard.regions.loading}
        regionsError={dashboard.regions.error}
        onRegionChange={setRegionId}
        onIndicatorChange={setIndicator}
        onFromChange={setFrom}
        onToChange={setTo}
        onGranularityChange={setGranularity}
        onMapLimitChange={setMapLimit}
        onReloadRegions={dashboard.regions.reload}
        onReset={resetFilters}
      />

      <div className="dashboard-toolbar">
        <DataFreshnessChip
          data={dashboard.dataFreshness.data}
          loading={dashboard.dataFreshness.loading}
          error={dashboard.dataFreshness.error}
          hasRegion={dashboard.hasRegion}
          onRetry={dashboard.dataFreshness.reload}
        />
        <SyncNowButton onSync={dashboard.syncNow} onSyncSuccess={dashboard.reloadAll} />
      </div>

      <LatestIndicatorsPanel
        data={dashboard.latestIndicator.data}
        loading={dashboard.latestIndicator.loading}
        error={dashboard.latestIndicator.error}
        hasRegion={dashboard.hasRegion}
        selectedRegionName={selectedRegion?.nombre || '-'}
        selectedRegionCode={selectedRegion?.codigo || '-'}
        onRetry={dashboard.latestIndicator.reload}
      />

      <DashboardKpiCards
        summary={dashboard.summary.data}
        loading={dashboard.summary.loading}
        error={dashboard.summary.error}
        onRetry={dashboard.summary.reload}
      />

      <div className="dashboard-grid">
        <LossTrendChart
          data={dashboard.lossTrend.data}
          loading={dashboard.lossTrend.loading}
          error={dashboard.lossTrend.error}
          onRetry={dashboard.lossTrend.reload}
        />
        <AlertsDonut
          data={dashboard.alertsSummary.data}
          loading={dashboard.alertsSummary.loading}
          error={dashboard.alertsSummary.error}
          onRetry={dashboard.alertsSummary.reload}
        />
        <AlertsBar
          data={dashboard.alertsSummary.data}
          loading={dashboard.alertsSummary.loading}
          error={dashboard.alertsSummary.error}
          onRetry={dashboard.alertsSummary.reload}
        />
      </div>

      <div className="dashboard-grid">
        <IndicatorSeriesChart
          data={dashboard.indicatorSeries.data}
          loading={dashboard.indicatorSeries.loading}
          error={dashboard.indicatorSeries.error}
          indicator={filters.indicator}
          hasRegion={dashboard.hasRegion}
          onRetry={dashboard.indicatorSeries.reload}
        />
      </div>

      <IndicatorsMapLayer
        data={dashboard.indicatorMap.data}
        loading={dashboard.indicatorMap.loading}
        error={dashboard.indicatorMap.error}
        indicator={filters.indicator}
        mapLimit={filters.mapLimit}
        onRetry={dashboard.indicatorMap.reload}
      />

      <CriticalRegionsTable
        data={dashboard.criticalRegions.data}
        loading={dashboard.criticalRegions.loading}
        error={dashboard.criticalRegions.error}
        onRetry={dashboard.criticalRegions.reload}
      />
    </section>
  );
}

export default DashboardPage;
