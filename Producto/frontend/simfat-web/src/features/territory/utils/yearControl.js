// Pure helpers for the MapBiomas year control (Q31). Generic on purpose: any
// yearly MapBiomas layer (BURNED, later LANDCOVER) plugs in with its own range.

// Years for the <select>, newest first.
export function yearOptions(minYear, maxYear) {
  const years = [];
  for (let year = maxYear; year >= minYear; year -= 1) {
    years.push(year);
  }
  return years;
}

// Year the control shows: while a stale layer (previous year) is displayed the
// control reflects the year being loaded, not the year of the visible polygons.
export function controlYear({ layer, selectedYear, defaultYear, stale }) {
  return stale ? selectedYear ?? defaultYear : layer?.year ?? selectedYear;
}

// Text for the polite live region: only when a layer for exactly this year is
// loaded. Failed or empty loads stay silent (the visible error lives elsewhere).
export function yearAnnouncement({ label, year, layerYear, loading }) {
  if (loading || !Number.isInteger(year) || layerYear !== year) {
    return '';
  }
  return `${label}: datos del año ${year} cargados.`;
}

// Bounds for the step buttons and "default" for the "Último" button, all
// derived from the year the control currently shows.
export function yearControlState({ year, range, defaultYear }) {
  return {
    atMin: year <= range.minYear,
    atMax: year >= range.maxYear,
    isDefault: Number.isInteger(defaultYear) && year === defaultYear
  };
}

// "Último" prefers the explicit default year (cache hit); null lets the
// backend pick when the default is not known yet.
export function latestYearTarget(defaultYear) {
  return Number.isInteger(defaultYear) ? defaultYear : null;
}
