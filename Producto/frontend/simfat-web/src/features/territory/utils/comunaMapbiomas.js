import { NEUTRAL_FILL } from '../constants/colorScales';
import { LANDCOVER_CLASSES } from '../constants/landcoverClasses';
import { formatShareAsPercent } from './landcoverLayer';
import { formatFractionAsPercent } from './mapbiomasLayer';

// Pure view-model helpers for the MapBiomas section of the comuna panel.
// The payload comes from GET /api/territory/public/mapbiomas/{comunaId}.

const TOP_CLASSES = 5;
const UNOBSERVED_MIN_SHARE = 0.01;
const MIN_VISIBLE_BAR_PCT = 4;
const DASH = '—';
const INDEX_FORMAT = new Intl.NumberFormat('es-CL', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const HECTARES_FORMAT = new Intl.NumberFormat('es-CL', { maximumFractionDigits: 1 });
const WEIGHT_FORMAT = new Intl.NumberFormat('es-CL', { minimumFractionDigits: 0, maximumFractionDigits: 3 });

function isNumber(value) {
  return typeof value === 'number' && Number.isFinite(value);
}

// An omitted year (backend default) lives in its own slot, like the layer cache.
export function comunaMapbiomasCacheKey(comunaId, year) {
  return `${comunaId}|${year === undefined || year === null ? 'default' : year}`;
}

// Bounded insertion-ordered cache: re-setting a key moves it to the newest slot
// and the oldest entries are evicted once the cap is exceeded.
export function boundedCacheSet(cache, key, value, maxEntries) {
  cache.delete(key);
  cache.set(key, value);
  while (cache.size > maxEntries) {
    cache.delete(cache.keys().next().value);
  }
}

// Returns the cached data when still fresh; expired entries are dropped.
export function readFreshCache(cache, key, now) {
  const entry = cache.get(key);
  if (entry && now < entry.expiresAt) {
    return entry.data;
  }
  cache.delete(key);
  return null;
}

// Stale-while-revalidate: previous data is only reusable for the same comuna,
// never under another comuna's header.
export function staleDataFor(previous, comunaId) {
  return previous && previous.comunaId === comunaId && previous.data ? previous.data : null;
}

// Decides what the sidebar renders for a selected comuna.
export function panelMode({ comunaId, score } = {}) {
  if (!comunaId) return 'none';
  return score ? 'risk' : 'mapbiomas-only';
}

// Where the panel was opened from decides the section order: a click owned by a
// MapBiomas layer shows the MapBiomas section first.
export function comunaPanelOrigin(ownerId) {
  return ownerId === 'BURNED' || ownerId === 'LANDCOVER' ? 'mapbiomas' : 'risk';
}

// The panel follows the LANDCOVER layer's year only while that layer is visible;
// otherwise the backend default applies.
export function panelYear({ visible, selectedYear } = {}) {
  return visible && Number.isInteger(selectedYear) ? selectedYear : undefined;
}

export function coverageView(landCover) {
  const year = landCover?.year;
  const shares = Array.isArray(landCover?.shares) ? landCover.shares.filter((item) => isNumber(item?.share)) : [];
  if (!landCover || landCover.available !== true || shares.length === 0) {
    return {
      state: 'empty',
      message: Number.isInteger(year) ? `Sin datos de cobertura para ${year}` : 'Sin datos de cobertura'
    };
  }

  const sorted = [...shares].sort((a, b) => b.share - a.share);
  const bars = sorted.slice(0, TOP_CLASSES).map((item) => ({
    code: item.code,
    label: item.label || LANDCOVER_CLASSES[item.code]?.label || `Clase ${item.code}`,
    color: LANDCOVER_CLASSES[item.code]?.color ?? NEUTRAL_FILL,
    share: item.share,
    text: formatShareAsPercent(item.share)
  }));

  const rest = sorted.slice(TOP_CLASSES).reduce((sum, item) => sum + item.share, 0);
  const others = rest > 0 ? { share: rest, text: formatShareAsPercent(rest) } : null;
  const unobservedText =
    isNumber(landCover.unobservedShare) && landCover.unobservedShare >= UNOBSERVED_MIN_SHARE
      ? `No observado ${formatShareAsPercent(landCover.unobservedShare)}`
      : null;

  const parts = bars.map((bar) => `${bar.label} ${bar.text}`);
  if (others) parts.push(`Otras ${others.text}`);
  if (unobservedText) parts.push(unobservedText);

  return {
    state: 'ready',
    title: Number.isInteger(year) ? `Cobertura ${year}` : 'Cobertura',
    bars,
    others,
    unobservedText,
    summary: parts.join(', ')
  };
}

export function fireView(fire, fireHistoryAvailable, highlightYear) {
  const entries = Object.entries(fire?.burnedHaByYear || {})
    .map(([year, ha]) => ({ year: Number(year), ha: isNumber(ha) && ha >= 0 ? ha : 0 }))
    .filter((entry) => Number.isInteger(entry.year))
    .sort((a, b) => a.year - b.year);
  if (!fireHistoryAvailable || !fire || fire.available !== true || entries.length === 0) {
    return { state: 'empty', message: 'Sin historial de fuego disponible' };
  }

  const maxHa = Math.max(...entries.map((entry) => entry.ha));
  const bars = entries.map((entry) => {
    let heightPct = maxHa > 0 ? (entry.ha / maxHa) * 100 : 0;
    if (entry.ha > 0) heightPct = Math.max(heightPct, MIN_VISIBLE_BAR_PCT);
    const highlighted = entry.year === highlightYear;
    // A highlighted year without fire still gets a visible marker.
    if (highlighted && entry.ha === 0) heightPct = MIN_VISIBLE_BAR_PCT;
    return {
      year: entry.year,
      ha: entry.ha,
      heightPct,
      highlighted,
      label: `${entry.year}: ${HECTARES_FORMAT.format(entry.ha)} ha`
    };
  });

  const range = `${entries[0].year}–${entries[entries.length - 1].year}`;
  const selected = bars.find((bar) => bar.highlighted);
  return {
    state: 'ready',
    title: `Fuego ${range}`,
    bars,
    firstYearLabel: String(entries[0].year),
    lastYearLabel: String(entries[entries.length - 1].year),
    selectedText: selected ? `Año seleccionado ${selected.year}: ${HECTARES_FORMAT.format(selected.ha)} ha` : null,
    cumulativeText: `Área quemada acumulada ${range}: ${formatFractionAsPercent(fire.burnedFraction)}`,
    lastFireText: Number.isInteger(fire.yearLastFire)
      ? `Último año con fuego: ${fire.yearLastFire}`
      : 'Sin fuego registrado en el período',
    summary: bars.map((bar) => bar.label).join('; ')
  };
}

export function formatIndex(value) {
  return isNumber(value) ? INDEX_FORMAT.format(value) : DASH;
}

export function indicesRows(indices) {
  return [
    { label: 'Combustible', value: formatIndex(indices?.fuelIndex) },
    { label: 'Historial de fuego', value: formatIndex(indices?.historyIndex) },
    { label: 'S_MapBiomas', value: formatIndex(indices?.sMapbiomas) }
  ];
}

export function formatWeight(value) {
  const weight = toWeight(value);
  return weight === null ? DASH : WEIGHT_FORMAT.format(weight);
}

// Weight may arrive as a number or a numeric string; anything else is unknown.
function toWeight(value) {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
  const weight = Number(value);
  return Number.isFinite(weight) && weight >= 0 ? weight : null;
}

// Honest wording about the risk score: weightedComponent is deliberately not
// used, because it is not the net change MapBiomas makes to the score.
export function scoreSentences(indices) {
  const sentences = [];
  const weight = toWeight(indices?.weight);
  if (weight === null) {
    sentences.push('No se pudo determinar si MapBiomas entra al cálculo del riesgo.');
  } else if (weight === 0) {
    sentences.push('MapBiomas no entra hoy al cálculo del riesgo (peso 0); se muestra como contexto.');
  } else if (indices?.enteredInScore === true) {
    sentences.push(`MapBiomas aporta al riesgo con peso ${formatWeight(weight)}.`);
    sentences.push('Es uno de los componentes que se combinan en el puntaje de riesgo.');
  } else if (indices?.sMapbiomas === null) {
    sentences.push('MapBiomas no se usó en el riesgo de esta comuna por falta de datos válidos.');
  } else {
    sentences.push('No se pudo determinar si MapBiomas entra al cálculo del riesgo.');
  }
  sentences.push('El riesgo es el valor actual y no depende del año seleccionado en el mapa.');
  return sentences;
}

// Fixed Spanish copy: raw error text never reaches the UI.
export function comunaMapbiomasErrorView(error) {
  if (error?.status === 404) {
    return { message: 'Sin datos MapBiomas para esta comuna.', canRetry: false };
  }
  if (error?.status === 400) {
    return { message: 'Sin datos MapBiomas para este año.', canRetry: false };
  }
  return { message: 'No fue posible cargar los datos MapBiomas.', canRetry: true };
}
