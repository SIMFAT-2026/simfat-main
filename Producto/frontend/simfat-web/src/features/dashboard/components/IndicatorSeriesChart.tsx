import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts';
import type { IndicatorSeriesPointDto } from '../types';
import DashboardPanelState from './DashboardPanelState';

interface IndicatorSeriesChartProps {
  data: IndicatorSeriesPointDto[] | null;
  loading: boolean;
  error: string;
  indicator: 'NDVI' | 'NDMI';
  hasRegion: boolean;
  onRetry: () => Promise<void> | void;
}

function IndicatorSeriesChart({ data, loading, error, indicator, hasRegion, onRetry }: IndicatorSeriesChartProps) {
  if (!hasRegion) {
    return (
      <article className="dashboard-card">
        <h3>Serie temporal {indicator}</h3>
        <p className="dashboard-hint">Selecciona una region para cargar la serie temporal del indicador.</p>
      </article>
    );
  }

  const rows = (data || []).map((point) => ({
    ...point,
    value: point.value ?? null
  }));
  const hasSomeValue = rows.some((point) => point.value !== null);

  return (
    <article className="dashboard-card">
      <h3>Serie temporal {indicator}</h3>
      <DashboardPanelState
        loading={loading}
        error={error}
        isEmpty={rows.length === 0 || !hasSomeValue}
        emptyTitle="Sin serie temporal"
        emptyDescription="No hay valores numericos para la combinacion de filtros seleccionada."
        onRetry={onRetry}
      >
        <ResponsiveContainer width="100%" height={300}>
          <AreaChart data={rows}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="ts" />
            <YAxis domain={[-1, 1]} />
            <Tooltip />
            <Legend />
            <Area
              type="monotone"
              dataKey="value"
              name={indicator}
              stroke={indicator === 'NDVI' ? '#16a34a' : '#0b5cab'}
              fill={indicator === 'NDVI' ? '#bbf7d0' : '#bfdbfe'}
              strokeWidth={2.1}
            />
          </AreaChart>
        </ResponsiveContainer>
      </DashboardPanelState>
    </article>
  );
}

export default IndicatorSeriesChart;
