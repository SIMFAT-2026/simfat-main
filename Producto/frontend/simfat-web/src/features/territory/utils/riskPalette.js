import { ALERT_LEVEL_CONFIG, RISK_FILL_OPACITY, RISK_NO_DATA } from '../constants/colorScales';

// Pure colour helpers for the risk choropleth. The map draws translucent fills over
// OpenStreetMap, so "does this palette read well" has to be judged on the *blended*
// colour, not on the raw fill. Everything here is DOM-free so it can be unit tested.

// Typical OSM land colour; the legend swatches are blended over it too, so the legend
// shows what the map shows.
export const LAND_COLOR = '#f2efe9';

const LEVEL_ORDER = ['NORMAL', 'PREVENTIVO', 'ALTO', 'CRITICO'];

function hexToRgb(hex) {
  return [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
}

function rgbToHex(rgb) {
  return `#${rgb.map((c) => Math.round(c).toString(16).padStart(2, '0')).join('')}`;
}

export function blendOver(foreground, background, opacity) {
  const fg = hexToRgb(foreground);
  const bg = hexToRgb(background);
  return rgbToHex(fg.map((c, i) => c * opacity + bg[i] * (1 - opacity)));
}

function srgbToLinear(channel) {
  const c = channel / 255;
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

function hexToLab(hex) {
  const [r, g, b] = hexToRgb(hex).map(srgbToLinear);
  const x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047;
  const y = 0.2126729 * r + 0.7151522 * g + 0.072175 * b;
  const z = (0.0193339 * r + 0.119192 * g + 0.9503041 * b) / 1.08883;
  const f = (t) => (t > 216 / 24389 ? Math.cbrt(t) : ((24389 / 27) * t + 16) / 116);
  const fx = f(x);
  const fy = f(y);
  const fz = f(z);
  return [116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)];
}

const RAD = Math.PI / 180;
const DEG = 180 / Math.PI;

function hueAngle(b, a) {
  if (a === 0 && b === 0) return 0;
  const h = Math.atan2(b, a) * DEG;
  return h < 0 ? h + 360 : h;
}

// CIEDE2000 between two CIELAB colours (Sharma, Wu & Dalal 2005).
export function deltaE2000([l1, a1, b1], [l2, a2, b2]) {
  const c1 = Math.hypot(a1, b1);
  const c2 = Math.hypot(a2, b2);
  const cBar = (c1 + c2) / 2;
  const g = 0.5 * (1 - Math.sqrt(cBar ** 7 / (cBar ** 7 + 25 ** 7)));
  const a1p = (1 + g) * a1;
  const a2p = (1 + g) * a2;
  const c1p = Math.hypot(a1p, b1);
  const c2p = Math.hypot(a2p, b2);
  const h1 = hueAngle(b1, a1p);
  const h2 = hueAngle(b2, a2p);

  const dL = l2 - l1;
  const dC = c2p - c1p;
  let dh = 0;
  if (c1p * c2p !== 0) {
    dh = h2 - h1;
    if (dh > 180) dh -= 360;
    else if (dh < -180) dh += 360;
  }
  const dH = 2 * Math.sqrt(c1p * c2p) * Math.sin((dh * RAD) / 2);

  const lBar = (l1 + l2) / 2;
  const cBarP = (c1p + c2p) / 2;
  let hBar = h1 + h2;
  if (c1p * c2p !== 0) {
    if (Math.abs(h1 - h2) <= 180) hBar = (h1 + h2) / 2;
    else hBar = (h1 + h2 + (h1 + h2 < 360 ? 360 : -360)) / 2;
  }
  const t =
    1 -
    0.17 * Math.cos((hBar - 30) * RAD) +
    0.24 * Math.cos(2 * hBar * RAD) +
    0.32 * Math.cos((3 * hBar + 6) * RAD) -
    0.2 * Math.cos((4 * hBar - 63) * RAD);
  const dTheta = 30 * Math.exp(-(((hBar - 275) / 25) ** 2));
  const rC = 2 * Math.sqrt(cBarP ** 7 / (cBarP ** 7 + 25 ** 7));
  const sL = 1 + (0.015 * (lBar - 50) ** 2) / Math.sqrt(20 + (lBar - 50) ** 2);
  const sC = 1 + 0.045 * cBarP;
  const sH = 1 + 0.015 * cBarP * t;
  const rT = -Math.sin(2 * dTheta * RAD) * rC;

  return Math.sqrt(
    (dL / sL) ** 2 + (dC / sC) ** 2 + (dH / sH) ** 2 + rT * (dC / sC) * (dH / sH)
  );
}

export function hexDeltaE(hexA, hexB) {
  return deltaE2000(hexToLab(hexA), hexToLab(hexB));
}

function relativeLuminance(hex) {
  const [r, g, b] = hexToRgb(hex).map(srgbToLinear);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

// WCAG 2.1 contrast ratio (1 to 21).
export function contrastRatio(hexA, hexB) {
  const a = relativeLuminance(hexA);
  const b = relativeLuminance(hexB);
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

// What each level (plus "sin datos") looks like on the map over bare land.
export function blendedLevelFills() {
  const fills = {};
  for (const level of LEVEL_ORDER) {
    fills[level] = blendOver(ALERT_LEVEL_CONFIG[level].fill, LAND_COLOR, RISK_FILL_OPACITY);
  }
  fills.NO_DATA = blendOver(RISK_NO_DATA.fill, LAND_COLOR, RISK_NO_DATA.fillOpacity);
  return fills;
}

// Leaflet path style for a comuna polygon in the risk layer.
export function comunaRiskStyle(score) {
  if (!score) {
    return {
      fillColor: RISK_NO_DATA.fill,
      fillOpacity: RISK_NO_DATA.fillOpacity,
      color: RISK_NO_DATA.color,
      weight: RISK_NO_DATA.weight,
      dashArray: RISK_NO_DATA.dashArray,
      opacity: 0.8
    };
  }
  const cfg = ALERT_LEVEL_CONFIG[score.alertLevel] || ALERT_LEVEL_CONFIG.NORMAL;
  return {
    fillColor: cfg.fill,
    fillOpacity: RISK_FILL_OPACITY,
    color: cfg.color,
    weight: cfg.weight,
    dashArray: cfg.dashArray,
    opacity: 0.9
  };
}

function borderStyleFor(dashArray) {
  if (!dashArray) return 'solid';
  return dashArray === RISK_NO_DATA.dashArray ? 'dotted' : 'dashed';
}

export function riskLegendItems() {
  const fills = blendedLevelFills();
  const levels = LEVEL_ORDER.map((level) => {
    const cfg = ALERT_LEVEL_CONFIG[level];
    return {
      key: level,
      text: cfg.legendText,
      note: cfg.note,
      swatchFill: fills[level],
      borderColor: cfg.color,
      borderStyle: borderStyleFor(cfg.dashArray)
    };
  });
  levels.push({
    key: 'NO_DATA',
    text: RISK_NO_DATA.text,
    note: undefined,
    swatchFill: fills.NO_DATA,
    borderColor: RISK_NO_DATA.color,
    borderStyle: 'dotted'
  });
  return levels;
}
