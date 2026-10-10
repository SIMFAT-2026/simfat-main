import { useId } from 'react';
import {
  controlYear,
  latestYearTarget,
  yearAnnouncement,
  yearControlState,
  yearOptions
} from '../utils/yearControl';

// One compact row for one active MapBiomas layer (Q31): label, ‹ select ›,
// "Último". `state` is the hook result for that layer: { selectedYear, range,
// defaultYear, layer, loading, stale, setYear }. Every change commits at once.
function MapbiomasYearControl({ label, state }) {
  const { range, defaultYear, layer, loading, setYear } = state;
  // While a stale (previous year) layer is shown, the control follows the
  // year being loaded, not the year of the visible polygons.
  const year = controlYear({
    layer,
    selectedYear: state.selectedYear,
    defaultYear: state.defaultYear,
    stale: state.stale
  });
  const baseId = useId();

  if (!range && !loading) return null;

  const hasYear = Number.isInteger(year);
  const { atMin, atMax, isDefault } = range && hasYear
    ? yearControlState({ year, range, defaultYear })
    : {};
  const labelId = `${baseId}-label`;
  const selectId = `${baseId}-select`;
  const stepTo = (disabled, target) => () => {
    if (disabled || (target !== null && !Number.isInteger(target))) return;
    setYear(target);
  };

  // The live region is always mounted (initially empty) and only its text
  // changes, so assistive tech registers it before the first announcement.
  return (
    <div className="territory-year-control" role="group" aria-labelledby={labelId} aria-busy={loading}>
      <label id={labelId} htmlFor={range ? selectId : undefined} className="territory-year-label">
        {label}
      </label>
      {range ? (
        <>
          <button
            type="button"
            className="btn btn-secondary territory-year-step"
            aria-label="Año anterior"
            aria-disabled={atMin ? 'true' : undefined}
            onClick={stepTo(atMin, year - 1)}
          >
            ‹
          </button>
          <select
            id={selectId}
            className="territory-year-select"
            aria-label={`Año de ${label}`}
            value={hasYear ? year : ''}
            onChange={(event) => setYear(Number(event.target.value))}
          >
            {!hasYear ? <option value="" /> : null}
            {yearOptions(range.minYear, range.maxYear).map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="btn btn-secondary territory-year-step"
            aria-label="Año siguiente"
            aria-disabled={atMax ? 'true' : undefined}
            onClick={stepTo(atMax, year + 1)}
          >
            ›
          </button>
          <button
            type="button"
            className="btn btn-secondary territory-year-latest"
            aria-disabled={isDefault ? 'true' : undefined}
            onClick={stepTo(isDefault, latestYearTarget(defaultYear))}
          >
            Último
          </button>
        </>
      ) : (
        <span className="territory-meta">Cargando…</span>
      )}
      <span className="sr-only" role="status" aria-live="polite">
        {yearAnnouncement({ label, year, layerYear: layer?.year, loading })}
      </span>
    </div>
  );
}

export default MapbiomasYearControl;
