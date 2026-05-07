import type { IndicatorMapPointDto } from '../types';
import DashboardPanelState from './DashboardPanelState';

interface IndicatorsMapLayerProps {
  data: IndicatorMapPointDto[] | null;
  loading: boolean;
  error: string;
  indicator: 'NDVI' | 'NDMI';
  mapLimit: number;
  onRetry: () => Promise<void> | void;
}

function getIntensityPercent(value: number | null): number {
  if (value === null) {
    return 0;
  }
  const normalized = Math.abs(value) * 100;
  return Math.max(4, Math.min(100, normalized));
}

function formatValue(value: number | null): string {
  if (value === null) {
    return '-';
  }
  return value.toFixed(3);
}

function IndicatorsMapLayer({ data, loading, error, indicator, mapLimit, onRetry }: IndicatorsMapLayerProps) {
  const rows = data || [];

  return (
    <article className="dashboard-card">
      <div className="map-layer-header">
        <h3>Capa de mapa {indicator}</h3>
        <span className="badge">{rows.length}/{mapLimit}</span>
      </div>
      <DashboardPanelState
        loading={loading}
        error={error}
        isEmpty={rows.length === 0}
        emptyTitle="Sin puntos para el mapa"
        emptyDescription="No hay datos para renderizar la capa de mapa del indicador."
        onRetry={onRetry}
      >
        <div className="map-layer-list">
          {rows.map((point) => (
            <div key={`${point.regionId}-${point.indicator}`} className="map-layer-item">
              <div className="map-layer-item-top">
                <strong>{point.regionName}</strong>
                <span>{formatValue(point.value)}</span>
              </div>
              <div className="map-layer-bar">
                <div
                  className={`map-layer-bar-fill map-layer-${point.criticity.toLowerCase()}`}
                  style={{ width: `${getIntensityPercent(point.value)}%` }}
                />
              </div>
            </div>
          ))}
        </div>
      </DashboardPanelState>
    </article>
  );
}

export default IndicatorsMapLayer;
