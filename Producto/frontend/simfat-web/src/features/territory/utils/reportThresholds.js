import { ALERT_LEVEL_CONFIG } from '../constants/colorScales';
import {
  FIRMS_COUNT_CRITICO,
  FIRMS_FRP_CRITICO,
  FWI_ALTO,
  FWI_CRITICO,
  SCORE_CRITICO,
} from './alertTrigger';

// Rules shown in the printed report. They reuse the constants behind the map legend
// so the PDF cannot drift from what the system actually applies to each comuna.
const dash = (range) => range.replace('-', '–');

export function levelRuleRows() {
  const { NORMAL, PREVENTIVO, ALTO, CRITICO } = ALERT_LEVEL_CONFIG;
  return [
    { level: 'NORMAL', label: NORMAL.label, rule: `Puntaje ${dash(NORMAL.range)}` },
    { level: 'PREVENTIVO', label: PREVENTIVO.label, rule: `Puntaje ${dash(PREVENTIVO.range)}` },
    { level: 'ALTO', label: ALTO.label, rule: `Puntaje ${dash(ALTO.range)} o FWI ≥ ${FWI_ALTO}` },
    {
      level: 'CRITICO',
      label: CRITICO.label,
      rule:
        `Puntaje ≥ ${SCORE_CRITICO * 100}, FWI ≥ ${FWI_CRITICO}, foco FIRMS hoy, ` +
        `≥ ${FIRMS_COUNT_CRITICO} focos o FRP medio ≥ ${FIRMS_FRP_CRITICO} MW`,
    },
  ];
}

// Canadian FWI class scale, kept only as a reading aid. It is NOT how the system
// assigns alert levels.
export const FWI_CLASS_NOTE =
  'Clases de referencia del FWI canadiense (no son los umbrales de alerta del sistema)';

export const FWI_REFERENCE_CLASSES = [
  { label: 'Bajo', range: '0 – 11', max: 11 },
  { label: 'Moderado', range: '11 – 21', max: 21 },
  { label: 'Alto', range: '21 – 38', max: 38 },
  { label: 'Muy alto', range: '38 – 50', max: 50 },
  { label: 'Extremo', range: '> 50', max: Infinity },
];

export function fwiClassLabel(value) {
  if (value == null || !Number.isFinite(value)) return '—';
  return FWI_REFERENCE_CLASSES.find((c) => value < c.max).label;
}

// "Clase FWI canadiense: Moderado": says which scale the class belongs to, so it is
// not confused with the system alert level (Normal / Preventivo / Alto / Crítico).
export function fwiClassText(value) {
  if (value == null || !Number.isFinite(value)) return '';
  return `Clase FWI canadiense: ${fwiClassLabel(value)}`;
}
