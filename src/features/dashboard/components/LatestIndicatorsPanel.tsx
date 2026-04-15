import type { LatestIndicatorDto } from '../types';
import DashboardPanelState from './DashboardPanelState';

interface LatestIndicatorsPanelProps {
  data: LatestIndicatorDto | null;
  loading: boolean;
  error: string;
  hasRegion: boolean;
  selectedRegionName: string;
  selectedRegionCode: string;
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

function getValueStatus(value: number | null): 'SALUDABLE' | 'INTERMEDIO' | 'CRITICO' | 'SIN_DATO' {
  if (value === null) {
    return 'SIN_DATO';
  }
  if (value >= 0.5) {
    return 'SALUDABLE';
  }
  if (value >= 0.2) {
    return 'INTERMEDIO';
  }
  return 'CRITICO';
}

function getValueBadgeClass(status: ReturnType<typeof getValueStatus>): string {
  if (status === 'SALUDABLE') {
    return 'badge badge-low';
  }
  if (status === 'INTERMEDIO') {
    return 'badge badge-medium';
  }
  if (status === 'CRITICO') {
    return 'badge badge-critical';
  }
  return 'badge';
}

function LatestIndicatorsPanel({
  data,
  loading,
  error,
  hasRegion,
  selectedRegionName,
  selectedRegionCode,
  onRetry
}: LatestIndicatorsPanelProps) {
  if (!hasRegion) {
    return (
      <article className="dashboard-card">
        <h3>Ultimo indicador</h3>
        <p className="dashboard-hint">Selecciona una region para consultar el ultimo valor NDVI/NDMI.</p>
      </article>
    );
  }

  const valueStatus = getValueStatus(data?.value ?? null);

  return (
    <article className="dashboard-card">
      <h3>KPI satelital actual</h3>
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
            <strong>{selectedRegionName}</strong>
            <p className="latest-indicator-sub">Codigo: {selectedRegionCode}</p>
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
            <strong>{formatTimestamp(data?.observedAt || '')}</strong>
          </div>
          <div>
            <p className="latest-indicator-label">Estado indice</p>
            <span className={getValueBadgeClass(valueStatus)}>{valueStatus}</span>
          </div>
          <div>
            <p className="latest-indicator-label">Fuente</p>
            <strong>{data?.source || '-'}</strong>
          </div>
          <div>
            <p className="latest-indicator-label">Cache</p>
            <span className="badge">{data?.cached ? 'CACHEADA' : 'EN VIVO'}</span>
          </div>
        </div>
      </DashboardPanelState>
    </article>
  );
}

export default LatestIndicatorsPanel;
