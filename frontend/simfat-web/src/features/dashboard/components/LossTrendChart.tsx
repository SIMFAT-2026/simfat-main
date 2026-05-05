import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts';
import type { LossTrendPointDto } from '../types';
import DashboardPanelState from './DashboardPanelState';

interface LossTrendChartProps {
  data: LossTrendPointDto[] | null;
  loading: boolean;
  error: string;
  onRetry: () => Promise<void> | void;
}

function LossTrendChart({ data, loading, error, onRetry }: LossTrendChartProps) {
  const rows = data || [];

  return (
    <article className="dashboard-card">
      <h3>Tendencia de perdida forestal</h3>
      <DashboardPanelState
        loading={loading}
        error={error}
        isEmpty={rows.length === 0}
        emptyTitle="Sin tendencia disponible"
        emptyDescription="No hay datos para graficar la tendencia de perdida."
        onRetry={onRetry}
      >
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={rows}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="label" />
            <YAxis />
            <Tooltip />
            <Legend />
            <Line type="monotone" dataKey="hectaresLost" name="Ha perdidas" stroke="#0b5cab" strokeWidth={2.2} />
          </LineChart>
        </ResponsiveContainer>
      </DashboardPanelState>
    </article>
  );
}

export default LossTrendChart;
