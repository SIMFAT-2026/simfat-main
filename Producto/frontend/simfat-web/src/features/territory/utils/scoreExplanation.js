// Builds the ready-to-render strings that explain a comuna risk score in the
// map tooltip. Pure (no DOM) so it can be unit-tested; the JSX only maps strings.

const STRUCTURAL_SUFFIX = ' (susceptibilidad estructural)';
const MAX_DRIFT_POINTS = 1;

// Fixed display order: structural baseline first, then current conditions.
const SEGMENTS = [
  { key: 'mapbiomas', label: 'MapBiomas', suffix: STRUCTURAL_SUFFIX },
  { key: 'fwi', label: 'FWI' },
  { key: 'ndmi', label: 'NDMI' },
  { key: 'ndvi', label: 'NDVI' },
  { key: 'firms', label: 'Focos FIRMS' },
  { key: 'reports', label: 'Reportes' }
];

const isNum = (v) => typeof v === 'number' && Number.isFinite(v);

const formatters = {};
function format(value, decimals) {
  if (!formatters[decimals]) {
    formatters[decimals] = new Intl.NumberFormat('es-CL', {
      minimumFractionDigits: 0,
      maximumFractionDigits: decimals
    });
  }
  // Normalize only negative zero (e.g. Math.round(-0.4)) so "-0" never renders.
  return formatters[decimals].format(Object.is(value, -0) ? 0 : value);
}

function isPresent(key, score) {
  const value = score.components?.[key];
  if (!isNum(value)) return false;
  if (key === 'mapbiomas') return isNum(score.mapbiomasWeight) && score.mapbiomasWeight > 0;
  // Deliberate: the service writes components.fwi = 0 when there is no weather
  // data, so a numeric component alone must not show a misleading "FWI 0".
  if (key === 'fwi') return isNum(score.fwiRaw);
  return true;
}

// Largest-remainder apportionment so rounded segments sum exactly to target.
// Returns null when the target cannot be reached by adding at most one point
// per segment.
function apportion(raws, target) {
  const floors = raws.map(Math.floor);
  const missing = target - floors.reduce((a, b) => a + b, 0);
  if (missing < 0 || missing > raws.length) return null;
  const order = raws
    .map((raw, index) => ({ index, remainder: raw - floors[index] }))
    .sort((a, b) => b.remainder - a.remainder || a.index - b.index);
  const points = [...floors];
  order.slice(0, missing).forEach(({ index }) => { points[index] += 1; });
  return points;
}

function buildCompositionLine(score) {
  if (!isNum(score.scoreComposite)) return null;
  const segments = SEGMENTS.filter(({ key }) => isPresent(key, score));
  if (!segments.length) return null;

  const raws = segments.map(({ key }) => score.components[key] * 100);
  const scorePoints = score.scoreComposite * 100;
  const target = Math.round(scorePoints);
  const drift = Math.abs(raws.reduce((a, b) => a + b, 0) - scorePoints);

  const apportioned = drift <= MAX_DRIFT_POINTS ? apportion(raws, target) : null;
  const points = apportioned || raws.map(Math.round);
  const body = segments
    .map(({ label, suffix = '' }, i) => `${label} ${points[i]}${suffix}`)
    .join(' · ');

  return apportioned ? `Aporte al puntaje: ${body}` : `Aporte al puntaje (aprox.): ${body}`;
}

function buildRawLine(score) {
  const parts = [];
  if (isNum(score.fwiRaw)) parts.push(`FWI ${format(score.fwiRaw, 1)}`);
  if (isNum(score.ndmiRaw)) parts.push(`NDMI ${format(score.ndmiRaw, 2)}`);
  if (isNum(score.ndviRaw)) parts.push(`NDVI ${format(score.ndviRaw, 2)}`);
  return parts.length ? parts.join(' · ') : null;
}

export function buildScoreExplanation(score) {
  if (!score) return { rawLine: null, compositionLine: null };
  return { rawLine: buildRawLine(score), compositionLine: buildCompositionLine(score) };
}

const WEATHER_ROW_KEYS = ['tempMin', 'tempMax', 'humidityMin', 'windMax'];

// The tooltip weather row needs at least one of temperature, humidity or wind;
// precipitation alone would render only placeholders.
export function hasWeatherRow(fwiInputs) {
  return WEATHER_ROW_KEYS.some((key) => isNum(fwiInputs?.[key]));
}

// Tooltip weather row, e.g. "8°/20° · 46% · 11 km/h · 3,7 mm" (es-CL decimals,
// a space before multi-letter units). Placeholders keep the columns stable.
export function formatWeatherRow(fwiInputs) {
  const { tempMin, tempMax, humidityMin, windMax, precip } = fwiInputs ?? {};
  let temp = '—';
  if (isNum(tempMin) && isNum(tempMax)) temp = `${format(Math.round(tempMin), 0)}°/${format(Math.round(tempMax), 0)}°`;
  else if (isNum(tempMax)) temp = `${format(Math.round(tempMax), 0)}°`;
  const parts = [
    temp,
    isNum(humidityMin) ? `${format(Math.round(humidityMin), 0)}%` : '—',
    isNum(windMax) ? `${format(Math.round(windMax), 0)} km/h` : '—'
  ];
  if (isNum(precip) && precip > 0) parts.push(`${format(precip, 1)} mm`);
  return parts.join(' · ');
}
