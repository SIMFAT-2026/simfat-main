import { useCallback, useEffect, useId, useRef, useState } from 'react';
import {
  controlYear,
  latestYearTarget,
  yearAnnouncement,
  yearControlLabel,
  yearControlState,
  yearKeyAction,
  yearOptions
} from '../utils/yearControl';

// Slider drags fire many change events; only the settled year is requested so
// the abortable loader is not flooded (the latest year always wins anyway).
const SLIDER_COMMIT_DELAY_MS = 200;

// One labelled year control for one active MapBiomas layer (Q31). `state` is
// the hook result for that layer: { selectedYear, range, defaultYear, layer,
// loading, setYear }. Buttons and the mobile <select> commit immediately.
function MapbiomasYearControl({ label, state }) {
  const { range, defaultYear, layer, loading, setYear } = state;
  // While a stale (previous year) layer is shown, the control follows the
  // year being loaded, not the year of the visible polygons.
  const loadedYear = controlYear({ layer, selectedYear: state.selectedYear, stale: state.stale });
  const baseId = useId();
  const [draft, setDraft] = useState(loadedYear);
  // While a new year loads there is no layer yet: show the year being fetched.
  const year = Number.isInteger(loadedYear) ? loadedYear : draft;
  const timerRef = useRef(null);

  useEffect(() => {
    if (Number.isInteger(loadedYear)) setDraft(loadedYear);
  }, [loadedYear]);

  useEffect(() => () => clearTimeout(timerRef.current), []);

  const commit = useCallback(
    (nextYear) => {
      clearTimeout(timerRef.current);
      // null means "back to the backend default year".
      setDraft(nextYear === null ? defaultYear : nextYear);
      setYear(nextYear);
    },
    [setYear, defaultYear]
  );

  const handleSliderChange = (event) => {
    const nextYear = Number(event.target.value);
    setDraft(nextYear);
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => setYear(nextYear), SLIDER_COMMIT_DELAY_MS);
  };

  // PageUp/PageDown are not steps of the native range input in every browser,
  // so the shortcuts are handled here; the native ones are suppressed for them.
  const handleKeyDown = (event) => {
    if (!range || !Number.isInteger(year)) return;
    const nextYear = yearKeyAction(event, draft ?? year, range.minYear, range.maxYear);
    if (nextYear === null) return;
    event.preventDefault();
    commit(nextYear);
  };

  const title = yearControlLabel(label, year);

  if (!range && !loading) return null;

  const { current, atMin, atMax, isDefault } = range
    ? yearControlState({ draft, loadedYear: year, range, defaultYear })
    : {};
  const labelId = `${baseId}-label`;
  const stepTo = (disabled, target) => () => {
    if (disabled) return;
    commit(target);
  };

  // The live region is always mounted (initially empty) and only its text
  // changes, so assistive tech registers it before the first announcement.
  return (
    <div className="territory-year-control" role="group" aria-labelledby={labelId} aria-busy={loading}>
      <span id={labelId} className="territory-year-label">{title}</span>
      {range ? (
        <>
          <button
            type="button"
            className="btn btn-secondary territory-year-step"
            aria-label="Año anterior"
            aria-disabled={atMin ? 'true' : undefined}
            onClick={stepTo(atMin, current - 1)}
          >
            ‹
          </button>
          <input
            type="range"
            className="territory-year-slider"
            aria-label={`Año de ${label}`}
            min={range.minYear}
            max={range.maxYear}
            step={1}
            value={current}
            aria-valuetext={String(current)}
            onChange={handleSliderChange}
            onKeyDown={handleKeyDown}
          />
          <select
            className="territory-year-select"
            aria-label={`Año de ${label}`}
            value={year}
            onChange={(event) => commit(Number(event.target.value))}
          >
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
            onClick={stepTo(atMax, current + 1)}
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
