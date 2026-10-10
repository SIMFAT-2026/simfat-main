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

function isNumber(value) {
  return typeof value === 'number' && Number.isFinite(value);
}

// An omitted year (backend default) lives in its own slot, like the layer cache.
export function comunaMapbiomasCacheKey(comunaId, year) {
  return `${comunaId}|${year === undefined || year === null ? 'default' : year}`;
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
    return {
      year: entry.year,
      ha: entry.ha,
      heightPct,
      highlighted: entry.year === highlightYear,
      label: `${entry.year}: ${HECTARES_FORMAT.format(entry.ha)} ha`
    };
  });

  const range = `${entries[0].year}–${entries[entries.length - 1].year}`;
  return {
    state: 'ready',
    title: `Fuego ${range}`,
    bars,
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

// Honest wording about the risk score: weightedComponent is deliberately not
// used, because it is not the net change MapBiomas makes to the score.
export function scoreSentences(indices) {
  const sentences = [];
  if (indices?.enteredInScore === true) {
    sentences.push(`MapBiomas aporta al riesgo con peso ${formatIndex(indices.weight)}.`);
    sentences.push('Es uno de los componentes que se combinan en el puntaje de riesgo.');
  } else if (indices?.weight === 0) {
    sentences.push('MapBiomas no entra hoy al cálculo del riesgo (peso 0); se muestra como contexto.');
  } else {
    sentences.push('MapBiomas no se usó en el riesgo de esta comuna por falta de datos válidos.');
  }
  sentences.push('El riesgo es el valor actual y no depende del año seleccionado en el mapa.');
  return sentences;
}

// Fixed Spanish copy: raw error text never reaches the UI.
export function comunaMapbiomasErrorView(error) {
  if (error?.status === 404) {
    return { message: 'Sin datos MapBiomas para esta comuna.', canRetry: false };
  }
  return { message: 'No fue posible cargar los datos MapBiomas.', canRetry: true };
}
