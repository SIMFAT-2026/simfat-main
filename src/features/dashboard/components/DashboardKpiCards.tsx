import MetricCard from '../../../components/MetricCard';
import type { DashboardSummaryDto } from '../types';
import DashboardPanelState from './DashboardPanelState';

interface DashboardKpiCardsProps {
  summary: DashboardSummaryDto | null;
  loading: boolean;
  error: string;
  onRetry: () => Promise<void> | void;
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat('es-CL').format(value);
}

function DashboardKpiCards({ summary, loading, error, onRetry }: DashboardKpiCardsProps) {
  return (
    <div className="dashboard-section">
      <h3>KPIs principales</h3>
      <DashboardPanelState
        loading={loading}
        error={error}
        isEmpty={!summary}
        emptyTitle="Sin datos de resumen"
        emptyDescription="Todavia no hay informacion consolidada para mostrar."
        onRetry={onRetry}
      >
        <div className="metrics-grid">
          <MetricCard label="Total ha perdidas" value={formatNumber(summary?.totalHectaresLost || 0)} />
          <MetricCard label="Total alertas" value={formatNumber(summary?.totalAlerts || 0)} />
          <MetricCard label="Periodo mas critico" value={summary?.worstPeriodLabel || '-'} />
          <MetricCard label="Tendencia" value={summary?.trendLabel || '-'} />
          <MetricCard label="Regiones monitoreadas" value={formatNumber(summary?.monitoredRegions || 0)} />
        </div>
      </DashboardPanelState>
    </div>
  );
}

export default DashboardKpiCards;
