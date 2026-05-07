import type { DataFreshnessDto } from '../types';

interface DataFreshnessChipProps {
  data: DataFreshnessDto | null;
  loading: boolean;
  error: string;
  hasRegion: boolean;
  onRetry: () => Promise<void> | void;
}

function getChipClass(data: DataFreshnessDto | null): string {
  if (!data) {
    return 'freshness-chip freshness-chip-unknown';
  }
  if (data.status === 'FRESH') {
    return 'freshness-chip freshness-chip-fresh';
  }
  if (data.status === 'STALE') {
    return 'freshness-chip freshness-chip-stale';
  }
  return 'freshness-chip freshness-chip-empty';
}

function formatDate(value: string): string {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('es-CL');
}

function formatAge(ageSeconds: number): string {
  if (!Number.isFinite(ageSeconds) || ageSeconds <= 0) {
    return '-';
  }
  const hours = ageSeconds / 3600;
  if (hours >= 24) {
    return `${(hours / 24).toFixed(1)} d`;
  }
  return `${hours.toFixed(1)} h`;
}

function DataFreshnessChip({ data, loading, error, hasRegion, onRetry }: DataFreshnessChipProps) {
  if (!hasRegion) {
    return <span className="freshness-chip freshness-chip-unknown">Frescura: selecciona region</span>;
  }

  if (loading) {
    return <span className="freshness-chip freshness-chip-unknown">Frescura: cargando...</span>;
  }

  if (error) {
    return (
      <button type="button" className="freshness-chip freshness-chip-stale freshness-action" onClick={onRetry}>
        Frescura: error (reintentar)
      </button>
    );
  }

  return (
    <span className={getChipClass(data)}>
      Frescura: {data?.status || 'EMPTY'} | Ult update: {formatDate(data?.lastUpdate || '')} | Edad:{' '}
      {formatAge(data?.ageSeconds || 0)}
    </span>
  );
}

export default DataFreshnessChip;
