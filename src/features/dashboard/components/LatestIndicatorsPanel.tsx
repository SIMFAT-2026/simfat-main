import type { LatestIndicatorDto } from '../types';
import DashboardPanelState from './DashboardPanelState';

interface LatestIndicatorsPanelProps {
  data: LatestIndicatorDto | null;
  loading: boolean;
  error: string;
  hasRegion: boolean;
  onRetry: () => Promise<void> | void;
}

function formatValue(value: number | null): string {
  if (value === null) {
    return '-';
  }
  return value.toFixed(3);
}

function formatTimestamp(value: string): string {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('es-CL');
}

function getQualityClass(quality: LatestIndicatorDto['quality']): string {
  if (quality === 'GOOD') {
    return 'badge badge-low';
  }
  if (quality === 'WARN') {
    return 'badge badge-medium';
  }
  if (quality === 'STALE') {
    return 'badge badge-high';
  }
  return 'badge';
}

function LatestIndicatorsPanel({ data, loading, error, hasRegion, onRetry }: LatestIndicatorsPanelProps) {
  if (!hasRegion) {
    return (
      <article className="dashboard-card">
        <h3>Ultimo indicador</h3>
        <p className="dashboard-hint">Selecciona una region para consultar el ultimo valor NDVI/NDMI.</p>
      </article>
    );
  }

  return (
    <article className="dashboard-card">
      <h3>Ultimo indicador</h3>
      <DashboardPanelState
        loading={loading}
        error={error}
        isEmpty={!data}
        emptyTitle="Sin lectura reciente"
        emptyDescription="Aun no tenemos un valor reciente para la region seleccionada."
        onRetry={onRetry}
      >
        <div className="latest-indicator-grid">
          <div>
            <p className="latest-indicator-label">Region</p>
            <strong>{data?.regionName || '-'}</strong>
          </div>
          <div>
            <p className="latest-indicator-label">Indicador</p>
            <strong>{data?.indicator || '-'}</strong>
          </div>
          <div>
            <p className="latest-indicator-label">Valor</p>
            <strong>{formatValue(data?.value ?? null)}</strong>
          </div>
          <div>
            <p className="latest-indicator-label">Ultima lectura</p>
            <strong>{formatTimestamp(data?.measuredAt || '')}</strong>
          </div>
          <div>
            <p className="latest-indicator-label">Calidad</p>
            <span className={getQualityClass(data?.quality || 'UNKNOWN')}>{data?.quality || 'UNKNOWN'}</span>
          </div>
        </div>
      </DashboardPanelState>
    </article>
  );
}

export default LatestIndicatorsPanel;
