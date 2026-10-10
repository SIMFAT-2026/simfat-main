import { LANDCOVER_CLASSES } from '../constants/landcoverClasses';
import { burnedTooltipText } from './mapbiomasLayer';

// Pure helpers for the yearly MapBiomas LANDCOVER layer (dominant class per comuna).

const NO_DATA_LINE = 'Sin datos de cobertura';

function isNumber(value) {
  return typeof value === 'number' && Number.isFinite(value);
}

// The single "has data" predicate of the LANDCOVER layer: available AND a
// dominant class from the official palette. It decides colour, opacity, legend
// counts, notes and tooltips. The payload label is authoritative; the palette
// label is only a fallback.
export function hasLandcoverData(entry) {
  return entry?.available === true && Boolean(LANDCOVER_CLASSES[entry.dominantClass]);
}

const hasDominant = hasLandcoverData;

// Land-cover shares read as whole percents (no legend bins to protect, unlike
// BURNED). A present class never reads as 0 %.
export function formatShareAsPercent(fraction) {
  if (!isNumber(fraction)) {
    return 'Sin datos';
  }
  const percent = Math.round(fraction * 100);
  if (percent === 0 && fraction > 0) {
    return '< 1 %';
  }
  return `${percent} %`;
}

function dominantLabelOf(entry) {
  return entry.dominantLabel || LANDCOVER_CLASSES[entry.dominantClass].label;
}

// Classes present among the comunas of the layer, most frequent first, so the
// legend never lists a class nobody can see on the map.
export function landcoverLegendClasses(layer) {
  const counts = new Map();
  const labels = new Map();
  Object.values(layer?.values || {}).forEach((entry) => {
    if (!hasDominant(entry)) return;
    counts.set(entry.dominantClass, (counts.get(entry.dominantClass) || 0) + 1);
    labels.set(entry.dominantClass, dominantLabelOf(entry));
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
  if (!hasDominant(entry)) {
    return NO_DATA_LINE;
  }
  const share = isNumber(entry.dominantShare) ? ` ${formatShareAsPercent(entry.dominantShare)}` : '';
  return `Cobertura ${year}: ${dominantLabelOf(entry)}${share}`;
}

// Tooltip lines (one per row) for one comuna of the LANDCOVER choropleth: name,
// year and the top 3 classes with whole percentages.
export function landcoverTooltipLines({ nombre, year, entry }) {
  if (!hasDominant(entry)) {
    return [nombre, `${NO_DATA_LINE} en ${year}`];
  }
  const top = (Array.isArray(entry.top) ? entry.top : [])
    .filter((item) => typeof item?.label === 'string' && item.label && isNumber(item.share) && item.share > 0)
    .slice(0, 3)
    .map((item) => `${item.label}: ${formatShareAsPercent(item.share)}`);
  if (top.length === 0) {
    const share = isNumber(entry.dominantShare) ? `: ${formatShareAsPercent(entry.dominantShare)}` : '';
    top.push(`${dominantLabelOf(entry)}${share}`);
  }
  return [nombre, `Cobertura ${year}`, ...top];
}

// Rows of the BURNED tooltip. BURNED owns hover when RISK_SCORE is off, so the
// LANDCOVER line (if any) is appended; without it this is just the burned text.
export function burnedTooltipLines({ nombre, year, entry, landcoverLine = '' }) {
  const lines = [burnedTooltipText({ nombre, year, entry })];
  if (landcoverLine) lines.push(landcoverLine);
  return lines;
}

export function landcoverToggleLabel(layer) {
  const base = 'Cobertura (MapBiomas)';
  return layer && isNumber(layer.year) ? `${base} · ${layer.year}` : base;
}
