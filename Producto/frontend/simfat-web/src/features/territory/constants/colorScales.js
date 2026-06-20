// Colorblind-safe risk ramp: low risk stays pale/cool, while severity increases through
// warmer and darker values so the map remains distinguishable by luminance plus stroke pattern.
export const ALERT_LEVEL_CONFIG = {
  NORMAL: {
    color: '#1e3a8a',
    darkUiColor: '#93c5fd',
    bg: '#dbeafe',
    label: 'Normal',
    fill: '#dbeafe',
    dashArray: null,
    weight: 0.8,
    patternLabel: 'solid'
  },
  PREVENTIVO: {
    color: '#78350f',
    darkUiColor: '#fbbf24',
    bg: '#fef3c7',
    label: 'Preventivo',
    fill: '#f59e0b',
    dashArray: '4 3',
    weight: 1.1,
    patternLabel: 'short dash'
  },
  ALTO: {
    color: '#7c2d12',
    darkUiColor: '#fb923c',
    bg: '#ffedd5',
    label: 'Alto',
    fill: '#b45309',
    dashArray: '8 4',
    weight: 1.4,
    patternLabel: 'long dash'
  },
  CRITICO: {
    color: '#3f1d0b',
    darkUiColor: '#fdba74',
    bg: '#f5e8dc',
    label: 'Crítico',
    fill: '#3f1d0b',
    dashArray: '2 3',
    weight: 1.8,
    patternLabel: 'dense dash'
  }
};

export const RISK_SCORE_LEGEND = Object.values(ALERT_LEVEL_CONFIG).map((item) => ({
  color: item.fill,
  label: item.label,
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
