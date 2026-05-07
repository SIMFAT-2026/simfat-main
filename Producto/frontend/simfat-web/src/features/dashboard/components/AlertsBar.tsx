import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { AlertsSummaryDto } from '../types';
import DashboardPanelState from './DashboardPanelState';

interface AlertsBarProps {
  data: AlertsSummaryDto[] | null;
  loading: boolean;
  error: string;
  onRetry: () => Promise<void> | void;
}

function AlertsBar({ data, loading, error, onRetry }: AlertsBarProps) {
  const rows = data || [];
  return (
    <article className="dashboard-card">
      <h3>Alertas por categoria (barras)</h3>
      <DashboardPanelState
        loading={loading}
        error={error}
        isEmpty={rows.length === 0}
        emptyTitle="Sin alertas para visualizar"
        emptyDescription="No hay informacion para construir el grafico de barras."
        onRetry={onRetry}
      >
        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={rows}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="category" />
            <YAxis />
            <Tooltip />
            <Bar dataKey="total" fill="#2563eb" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </DashboardPanelState>
    </article>
  );
}

export default AlertsBar;
