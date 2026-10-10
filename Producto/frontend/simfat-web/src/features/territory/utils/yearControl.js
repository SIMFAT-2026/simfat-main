// Pure helpers for the MapBiomas year control (Q31). Generic on purpose: any
// yearly MapBiomas layer (BURNED, later LANDCOVER) plugs in with its own range.

export const YEAR_PAGE_STEP = 5;

export function clampYear(year, minYear, maxYear) {
  if (!Number.isInteger(year)) {
    return minYear;
  }
  return Math.min(maxYear, Math.max(minYear, year));
}

// Next year for a keyboard key, or null when the key is not a year shortcut.
export function stepYear(year, key, minYear, maxYear) {
  switch (key) {
    case 'ArrowRight':
    case 'ArrowUp':
      return clampYear(year + 1, minYear, maxYear);
    case 'ArrowLeft':
    case 'ArrowDown':
      return clampYear(year - 1, minYear, maxYear);
    case 'PageUp':
      return clampYear(year + YEAR_PAGE_STEP, minYear, maxYear);
    case 'PageDown':
      return clampYear(year - YEAR_PAGE_STEP, minYear, maxYear);
    case 'Home':
      return minYear;
    case 'End':
      return maxYear;
    default:
      return null;
  }
}

// Years for the mobile <select>, newest first.
export function yearOptions(minYear, maxYear) {
  const years = [];
  for (let year = maxYear; year >= minYear; year -= 1) {
    years.push(year);
  }
  return years;
}

export function yearControlLabel(layerLabel, year) {
  return `${layerLabel} · Año: ${Number.isInteger(year) ? year : '…'}`;
}

// Text for the polite live region: only when a layer for exactly this year is
// loaded. Failed or empty loads stay silent (the visible error lives elsewhere).
export function yearAnnouncement({ label, year, layerYear, loading }) {
  if (loading || !Number.isInteger(year) || layerYear !== year) {
    return '';
  }
  return `${label}: datos del año ${year} cargados.`;
}

// Buttons act on the pending slider draft when there is one; "default" is a
// property of the year actually loaded.
export function yearControlState({ draft, loadedYear, range, defaultYear }) {
  const current = draft ?? loadedYear;
  return {
    current,
    atMin: current <= range.minYear,
    atMax: current >= range.maxYear,
    isDefault: Number.isInteger(defaultYear) && loadedYear === defaultYear
  };
}

// Key to year decision; any modifier leaves the browser/AT shortcut alone.
export function yearKeyAction(event, current, minYear, maxYear) {
  if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) {
    return null;
  }
  return stepYear(current, event.key, minYear, maxYear);
}

// "Último" prefers the explicit default year (cache hit); null lets the
// backend pick when the default is not known yet.
export function latestYearTarget(defaultYear) {
  return Number.isInteger(defaultYear) ? defaultYear : null;
}
