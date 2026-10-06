import { MAPBIOMAS_INDICATORS } from '../constants/indicators';

// Pure helpers for the yearly MapBiomas layers (BURNED, LANDCOVER).

const NO_DATA_LABEL = 'Sin datos';
const PERCENT_FORMAT = new Intl.NumberFormat('es-CL', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

const HECTARES_FORMAT = new Intl.NumberFormat('es-CL', { maximumFractionDigits: 1 });

function isNumber(value) {
  return typeof value === 'number' && Number.isFinite(value);
}

// A burned fraction is valid only when finite and non-negative; anything else
// is invalid data and must read as "no data", never as zero.
function isValidFraction(value) {
  return isNumber(value) && value >= 0;
}

// BURNED values are 0-1 fractions (unit "fraction"): 0.0247 -> "2,47 %".
export function formatFractionAsPercent(fraction) {
  if (!isNumber(fraction)) {
    return NO_DATA_LABEL;
  }
  const percent = fraction * 100;
  // A real, non-zero burned share must never read as an exact zero.
  if (percent > 0 && percent < 0.005) {
    return '< 0,01 %';
  }
  return `${PERCENT_FORMAT.format(percent)} %`;
}

// Counts behind the Q29 legend note: comunas with burned area, comunas with an
// observed zero, and comunas without data for the selected year.
export function burnedSummary(layer) {
  const entries = Object.values(layer?.values || {});
  const summary = { burned: 0, zero: 0, unavailable: 0, total: entries.length };

  entries.forEach((entry) => {
    if (!entry || entry.available !== true || !isValidFraction(entry.burnedFraction)) {
      summary.unavailable += 1;
    } else if (entry.burnedFraction > 0) {
      summary.burned += 1;
    } else {
      summary.zero += 1;
    }
  });

  return summary;
}

// Q29 legend note, derived from the payload (never tied to a specific year).
// The selector sentence is only shown once the year control exists.
export function burnedLegendNote(layer, { hasYearControl = false } = {}) {
  if (!layer || !isNumber(layer.year)) {
    return '';
  }
  const { burned, unavailable, total } = burnedSummary(layer);
  const noun = total === 1 ? 'comuna' : 'comunas';
  let note = `Año ${layer.year} · ${burned} de ${total} ${noun} con área quemada registrada`;
  if (unavailable > 0) {
    note += ` (${unavailable} sin datos)`;
  }
  note += '.';
  if (hasYearControl) {
    note += ' Usa el selector para explorar otros años.';
  }
  return note;
}

// Tooltip text for one comuna of the BURNED choropleth.
export function burnedTooltipText({ nombre, year, entry }) {
  const fraction = entry?.burnedFraction;
  if (entry?.available !== true || !isValidFraction(fraction)) {
    return `${nombre}: Sin datos de Fuego`;
  }
  if (fraction <= 0) {
    return `${nombre}: Sin área quemada en ${year}`;
  }
  let text = `${nombre}: Área quemada ${year}: ${formatFractionAsPercent(fraction)}`;
  if (isNumber(entry.burnedHa)) {
    text += ` · ${HECTARES_FORMAT.format(entry.burnedHa)} ha`;
  }
  return text;
}

// year is part of the key so data for one year can never be served as another;
// an omitted year (backend default) lives in its own "default" slot.
export function mapbiomasCacheKey({ indicator, year, regionId, publicMode }) {
  const yearPart = year === undefined || year === null ? 'default' : String(year);
  return `mapbiomas|${indicator}|${yearPart}|${regionId}|${publicMode ? 'public' : 'auth'}`;
}

// The year param is left out entirely when not chosen, so the backend's
// data-derived default year applies.
export function buildMapbiomasParams({ regionId, indicator, year }) {
  if (!MAPBIOMAS_INDICATORS.includes(indicator)) {
    throw new Error(`Not a MapBiomas indicator: ${indicator}`);
  }
  const params = { regionId, indicators: indicator };
  if (year !== undefined && year !== null) {
    params.year = year;
  }
  return params;
}
