import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import type { AlertsSummaryDto } from '../types';
import DashboardPanelState from './DashboardPanelState';

const COLORS = ['#0b5cab', '#f59e0b', '#16a34a', '#dc2626', '#6b7280'];

interface AlertsDonutProps {
  data: AlertsSummaryDto[] | null;
  loading: boolean;
  error: string;
  onRetry: () => Promise<void> | void;
}

function AlertsDonut({ data, loading, error, onRetry }: AlertsDonutProps) {
  const rows = data || [];

  return (
    <article className="dashboard-card">
      <h3>Alertas por categoria (donut)</h3>
      <DashboardPanelState
        loading={loading}
        error={error}
        isEmpty={rows.length === 0}
        emptyTitle="Sin alertas para visualizar"
        emptyDescription="No hay informacion para construir el donut de alertas."
        onRetry={onRetry}
      >
        <ResponsiveContainer width="100%" height={280}>
          <PieChart>
            <Pie data={rows} dataKey="total" nameKey="category" outerRadius={95} innerRadius={50}>
              {rows.map((entry, index) => (
                <Cell key={`${entry.category}-${index}`} fill={COLORS[index % COLORS.length]} />
              ))}
            </Pie>
            <Tooltip />
            <Legend />
          </PieChart>
        </ResponsiveContainer>
      </DashboardPanelState>
    </article>
  );
}

export default AlertsDonut;
