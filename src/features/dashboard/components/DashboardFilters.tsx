import type { DashboardFilters, Granularity, IndicatorType } from '../types';

interface DashboardFiltersProps {
  filters: DashboardFilters;
  onRegionChange: (value: string) => void;
  onIndicatorChange: (value: IndicatorType) => void;
  onFromChange: (value: string) => void;
  onToChange: (value: string) => void;
  onGranularityChange: (value: Granularity) => void;
  onMapLimitChange: (value: number) => void;
  onReset: () => void;
}

function DashboardFiltersPanel({
  filters,
  onRegionChange,
  onIndicatorChange,
  onFromChange,
  onToChange,
  onGranularityChange,
  onMapLimitChange,
  onReset
}: DashboardFiltersProps) {
  return (
    <div className="filter-bar dashboard-filters">
      <label>
        Region ID
        <input
          type="text"
          value={filters.regionId}
          onChange={(event) => onRegionChange(event.target.value.trim())}
          placeholder="Ej: region-001"
        />
      </label>

      <label>
        Indicador
        <select value={filters.indicator} onChange={(event) => onIndicatorChange(event.target.value as IndicatorType)}>
          <option value="NDVI">NDVI</option>
          <option value="NDMI">NDMI</option>
        </select>
      </label>

      <label>
        Desde
        <input type="date" value={filters.from} onChange={(event) => onFromChange(event.target.value)} />
      </label>

      <label>
        Hasta
        <input type="date" value={filters.to} onChange={(event) => onToChange(event.target.value)} />
      </label>

      <label>
        Granularidad
        <select value={filters.granularity} onChange={(event) => onGranularityChange(event.target.value as Granularity)}>
          <option value="day">day</option>
          <option value="week">week</option>
          <option value="month">month</option>
        </select>
      </label>

      <label>
        Limite mapa
        <input
          type="number"
          min={1}
          max={500}
          step={1}
          value={filters.mapLimit}
          onChange={(event) => onMapLimitChange(Number(event.target.value))}
        />
      </label>

      <div className="dashboard-filters-actions">
        <button type="button" className="btn btn-secondary" onClick={onReset}>
          Limpiar filtros
        </button>
      </div>
    </div>
  );
}

export default DashboardFiltersPanel;
