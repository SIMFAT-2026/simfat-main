// Risk ramp follows blue -> yellow -> orange -> dark red (ColorBrewer RdYlBu / YlOrRd family).
// Not green: green vs orange/red is the pair red-green colour blindness confuses (about 1 in
// 12 men); riskPalette tests check every pair under simulated protan/deutan/tritan vision.
// The map paints these fills at RISK_FILL_OPACITY over the base map, so the values were picked
// for their *blended* appearance (adjacent levels >= 20 delta E, checked in riskPalette tests).
// Stroke dash patterns stay as a second, colour-independent cue.
export const RISK_FILL_OPACITY = 0.55;

// "Sin datos" is deliberately not part of the ramp: light grey fill, dotted outline.
export const RISK_NO_DATA = {
  fill: '#cbd5e1',
  fillOpacity: 0.3,
  color: '#475569',
  weight: 0.9,
  dashArray: '1 5',
  text: 'Sin datos de riesgo'
};

export const ALERT_LEVEL_CONFIG = {
  // `range` mirrors the SCORE_* thresholds in ComunaRiskServiceImpl/TerritoryRiskServiceImpl
  // (score is 0-1 there, shown as /100 here) — keep these in sync if those constants change.
  NORMAL: {
    color: '#1e3a8a',
    darkUiColor: '#93c5fd',
    bg: '#dbeafe',
    label: 'Normal',
    range: '0-49',
    legendText: 'Normal (puntaje 0–49)',
    fill: '#1f78a0',
    dashArray: null,
    weight: 0.8,
    patternLabel: 'solid'
  },
  PREVENTIVO: {
    color: '#854d0e',
    darkUiColor: '#fde047',
    bg: '#fef9c3',
    label: 'Preventivo',
    range: '50-69',
    legendText: 'Preventivo (puntaje 50–69)',
    fill: '#fff176',
    dashArray: '4 3',
    weight: 1.1,
    patternLabel: 'short dash'
  },
  ALTO: {
    color: '#9a3412',
    darkUiColor: '#fb923c',
    bg: '#ffedd5',
    label: 'Alto',
    range: '70-84',
    legendText: 'Alto (puntaje 70–84 o FWI ≥ 20)',
    fill: '#e6550d',
    dashArray: '8 4',
    weight: 1.4,
    patternLabel: 'long dash'
  },
  CRITICO: {
    color: '#7f1d1d',
    darkUiColor: '#fca5a5',
    bg: '#fee2e2',
    label: 'Crítico',
    range: '85-100',
    legendText: 'Crítico (puntaje ≥ 85 o focos FIRMS)',
    note: 'también se activa con un foco FIRMS detectado hoy, o con FWI ≥ 45, sin importar el puntaje',
    fill: '#7f0016',
    dashArray: '2 3',
    weight: 1.8,
    patternLabel: 'dense dash'
  }
};

export const RISK_SCORE_LEGEND = Object.values(ALERT_LEVEL_CONFIG).map((item) => ({
  color: item.fill,
  label: item.label,
  range: item.range,
  text: item.legendText,
  note: item.note,
  dashArray: item.dashArray,
  borderColor: item.color,
  patternLabel: item.patternLabel
}));

// Per-variable color scales: each climate indicator gets its own bins/ramp tuned for fire-risk relevance.
// `bins` are ascending upper-bounds; the last entry's `max` is Infinity.
export const CLIMATE_SCALES = {
  WIND: {
    unit: 'km/h',
    label: 'Viento',
    bins: [
      { max: 10, color: '#a7f3d0', label: '< 10 (calma)' },
      { max: 20, color: '#5eead4', label: '10-20 (leve)' },
      { max: 35, color: '#38bdf8', label: '20-35 (moderado)' },
      { max: 50, color: '#2563eb', label: '35-50 (fuerte)' },
      { max: Infinity, color: '#1e3a8a', label: '> 50 (severo)' }
    ]
  },
  HUMIDITY: {
    unit: '%',
    label: 'Humedad relativa',
    bins: [
      { max: 20, color: '#045a8d', label: '< 20 (crítico)' },
      { max: 30, color: '#2b8cbe', label: '20-30 (bajo)' },
      { max: 50, color: '#74a9cf', label: '30-50 (moderado)' },
      { max: 70, color: '#bdc9e1', label: '50-70 (normal)' },
      { max: Infinity, color: '#f1eef6', label: '> 70 (alto)' }
    ]
  },
  AIR_TEMP: {
    unit: '°C',
    label: 'Temp. del aire',
    bins: [
      { max: 10, color: '#bfdbfe', label: '< 10' },
      { max: 20, color: '#93c5fd', label: '10-20' },
      { max: 25, color: '#fde047', label: '20-25' },
      { max: 30, color: '#fb923c', label: '25-30' },
      { max: 35, color: '#f97316', label: '30-35' },
      { max: Infinity, color: '#dc2626', label: '> 35' }
    ]
  },
  SOIL_TEMP: {
    unit: '°C',
    label: 'Temp. del suelo',
    bins: [
      { max: 10, color: '#bfdbfe', label: '< 10' },
      { max: 15, color: '#93c5fd', label: '10-15' },
      { max: 20, color: '#fde047', label: '15-20' },
      { max: 25, color: '#fb923c', label: '20-25' },
      { max: Infinity, color: '#dc2626', label: '> 25' }
    ]
  }
};

export const VEGETATION_SCALES = {
  NDVI: {
    label: 'Índice vegetación (NDVI)',
    valueKey: 'ndviRaw',
    bins: [
      { max: 0, color: '#edf8fb', label: '< 0 (no vegetado)' },
      { max: 0.2, color: '#b3cde3', label: '0-0.2 (escasa)' },
      { max: 0.5, color: '#8c96c6', label: '0.2-0.5 (moderada)' },
      { max: Infinity, color: '#88419d', label: '> 0.5 (densa)' }
    ]
  },
  NDMI: {
    label: 'Humedad vegetación (NDMI)',
    valueKey: 'ndmiRaw',
    bins: [
      { max: -0.1, color: '#dc2626', label: '< -0.1 (severa)' },
      { max: 0.1, color: '#facc15', label: '-0.1-0.1 (leve)' },
      { max: Infinity, color: '#0ea5e9', label: '> 0.1 (húmeda)' }
    ]
  }
};

// Fill for comunas without data (missing entry, available=false, null value).
export const NEUTRAL_FILL = '#cbd5e1';

// BURNED: annual burned fraction per comuna (0-1 in the payload, bins on percent).
// Colours: official MapBiomas Fuego Col 1 legend (FUEGO_COL1_LEYENDA.pdf, sha256
// a3f9ec02...), the "scar size" ramp. The Fuego legend has no ramp for an annual
// burned fraction, so this official ramp from the same family is reused here.
// Bins are fixed (identical for every year) so maps of different years compare
// directly. `max` is an exclusive upper bound on the fraction; the last is Infinity.
// An observed 0 gets its own very light class, distinct from NEUTRAL_FILL (no data).
export const BURNED_SCALE = {
  label: 'Área quemada',
  zero: { color: '#f1f5f9', label: 'Sin área quemada' },
  bins: [
    { max: 0.005, color: '#D0AE35', label: '< 0,5 %' },
    { max: 0.02, color: '#C68E2F', label: '0,5–2 %' },
    { max: 0.05, color: '#B96D2B', label: '2–5 %' },
    { max: 0.2, color: '#AC4B28', label: '5–20 %' },
    { max: Infinity, color: '#72251D', label: '> 20 %' }
  ]
};

// entry: { available, burnedFraction, burnedHa } from the BURNED value map.
// A negative or non-finite fraction is invalid data: treated as no data, never zero.
export function burnedColorFor(entry) {
  const fraction = entry?.burnedFraction;
  if (entry?.available !== true || typeof fraction !== 'number' || !Number.isFinite(fraction) || fraction < 0) {
    return NEUTRAL_FILL;
  }
  if (fraction <= 0) {
    return BURNED_SCALE.zero.color;
  }
  const bin = BURNED_SCALE.bins.find((candidate) => fraction < candidate.max);
  return bin.color;
}
