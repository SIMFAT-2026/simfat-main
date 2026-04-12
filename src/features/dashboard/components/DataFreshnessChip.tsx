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
  return data.isFresh ? 'freshness-chip freshness-chip-fresh' : 'freshness-chip freshness-chip-stale';
}

function formatDate(value: string): string {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('es-CL');
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
      Frescura: {data?.isFresh ? 'actualizada' : 'desfasada'} | Ult sync: {formatDate(data?.lastSyncAt || '')}
    </span>
  );
}

export default DataFreshnessChip;
