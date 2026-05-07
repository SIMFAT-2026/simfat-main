import type { CriticalRegionDto } from '../types';
import DashboardPanelState from './DashboardPanelState';

interface CriticalRegionsTableProps {
  data: CriticalRegionDto[] | null;
  loading: boolean;
  error: string;
  onRetry: () => Promise<void> | void;
}

function getBadgeClass(level: CriticalRegionDto['criticity']): string {
  if (level === 'HIGH') {
    return 'badge badge-high';
  }
  if (level === 'MEDIUM') {
    return 'badge badge-medium';
  }
  if (level === 'LOW') {
    return 'badge badge-low';
  }
  if (level === 'CRITICAL') {
    return 'badge badge-critical';
  }
  return 'badge';
}

function formatLevel(level: CriticalRegionDto['criticity']): string {
  if (level === 'HIGH') {
    return 'HIGH';
  }
  if (level === 'MEDIUM') {
    return 'MEDIUM';
  }
  if (level === 'LOW') {
    return 'LOW';
  }
  if (level === 'CRITICAL') {
    return 'CRITICAL';
  }
  return 'UNKNOWN';
}

function CriticalRegionsTable({ data, loading, error, onRetry }: CriticalRegionsTableProps) {
  const rows = data || [];
  return (
    <article className="dashboard-card">
      <h3>Regiones criticas</h3>
      <DashboardPanelState
        loading={loading}
        error={error}
        isEmpty={rows.length === 0}
        emptyTitle="Sin regiones criticas"
        emptyDescription="No hay regiones con criticidad reportada en este momento."
        onRetry={onRetry}
      >
        <div className="table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th>Region</th>
                <th>Criticidad</th>
                <th>Ha perdidas</th>
                <th>Alertas</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((region) => (
                <tr key={region.id}>
                  <td>{region.regionName}</td>
                  <td>
                    <span className={getBadgeClass(region.criticity)}>{formatLevel(region.criticity)}</span>
                  </td>
                  <td>{new Intl.NumberFormat('es-CL').format(region.hectaresLost)}</td>
                  <td>{new Intl.NumberFormat('es-CL').format(region.totalAlerts)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </DashboardPanelState>
    </article>
  );
}

export default CriticalRegionsTable;
