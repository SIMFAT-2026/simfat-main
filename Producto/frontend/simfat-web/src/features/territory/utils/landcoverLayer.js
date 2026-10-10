import { LANDCOVER_CLASSES } from '../constants/landcoverClasses';
import { formatFractionAsPercent } from './mapbiomasLayer';

// Pure helpers for the yearly MapBiomas LANDCOVER layer (dominant class per comuna).

const NO_DATA_LINE = 'Sin datos de cobertura';

function isNumber(value) {
  return typeof value === 'number' && Number.isFinite(value);
}

// The payload label is authoritative; the official palette is only a fallback.
function hasDominant(entry) {
  return (
    entry?.available === true &&
    Boolean(LANDCOVER_CLASSES[entry.dominantClass]) &&
    typeof (entry.dominantLabel || LANDCOVER_CLASSES[entry.dominantClass].label) === 'string'
  );
}

// Classes present among the comunas of the layer, most frequent first, so the
// legend never lists a class nobody can see on the map.
export function landcoverLegendClasses(layer) {
  const counts = new Map();
  const labels = new Map();
  Object.values(layer?.values || {}).forEach((entry) => {
    if (!hasDominant(entry)) return;
    counts.set(entry.dominantClass, (counts.get(entry.dominantClass) || 0) + 1);
    labels.set(entry.dominantClass, entry.dominantLabel || LANDCOVER_CLASSES[entry.dominantClass].label);
  });
  return [...counts.entries()]
    .map(([code, count]) => ({
      code,
      label: labels.get(code),
      color: LANDCOVER_CLASSES[code].color,
      count
    }))
    .sort((a, b) => b.count - a.count || a.code - b.code);
}

// Factual legend note derived from the payload (never tied to a specific year).
export function landcoverLegendNote(layer, { hasYearControl = false } = {}) {
  if (!layer || !isNumber(layer.year)) {
    return '';
  }
  const entries = Object.values(layer.values || {});
  const total = entries.length;
  const recorded = entries.filter(hasDominant).length;
  const unavailable = total - recorded;
  let note = `Año ${layer.year} · ${recorded} de ${total} ${total === 1 ? 'comuna' : 'comunas'} con clase dominante registrada`;
  if (unavailable > 0) {
    note += ` (${unavailable} sin datos)`;
  }
  note += '.';
  if (hasYearControl) {
    note += ' Usa el selector para explorar otros años.';
  }
  return note;
}

// Dominant-cover sentence for one comuna, without its name (risk tooltip line).
export function landcoverLineFor(entry, year) {
  if (!hasDominant(entry) || !isNumber(entry.dominantShare)) {
    return NO_DATA_LINE;
  }
  const label = entry.dominantLabel || LANDCOVER_CLASSES[entry.dominantClass].label;
  return `Cobertura ${year}: ${label} ${formatFractionAsPercent(entry.dominantShare)}`;
}

// Tooltip lines (one per row) for one comuna of the LANDCOVER choropleth: name,
// year and the top 3 classes with truncated percentages.
export function landcoverTooltipLines({ nombre, year, entry }) {
  if (entry?.available !== true) {
    return [nombre, `${NO_DATA_LINE} en ${year}`];
  }
  const top = (Array.isArray(entry.top) ? entry.top : [])
    .filter((item) => typeof item?.label === 'string' && item.label && isNumber(item.share) && item.share >= 0)
    .slice(0, 3)
    .map((item) => `${item.label}: ${formatFractionAsPercent(item.share)}`);
  return [nombre, `Cobertura ${year}`, ...top];
}

export function landcoverToggleLabel(layer) {
  const base = 'Cobertura (MapBiomas)';
  return layer && isNumber(layer.year) ? `${base} · ${layer.year}` : base;
}
