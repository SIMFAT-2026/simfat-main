// Pure helpers shared by the alerts hook and views (no React, no I/O), so they
// can be unit tested directly once a frontend test runner exists.

export const RISK_LEVELS = ['BAJO', 'MEDIO', 'ALTO', 'CRITICO'];
export const ALERT_LEVEL_ORDER = { CRITICO: 0, ALTO: 1, PREVENTIVO: 2, NORMAL: 3 };

// Backend rules for GET /api/alerts/public and /api/citizen-reports/public.
export const PUBLIC_MAX_SPAN_DAYS = 90;
// One day of margin below the backend's 90-day limit.
const PUBLIC_CLIENT_SPAN_DAYS = 89;
const DEFAULT_WINDOW_DAYS = 30;
export const PUBLIC_MIN_DATE = '2000-01-01';
export const PUBLIC_ALERTS_CAP = 1000;
export const PUBLIC_REPORTS_CAP = 500;
export const PUBLIC_DEFAULT_REGION_ID = 'biobio';

export function normalizeDateTime(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toISOString();
}

export function formatDateTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value || '-';
  }
  return date.toLocaleString('es-CL');
}

export function normalizeReport(item) {
  const normalizedStatus = String(item.status || item.estado || 'RECIBIDO').toUpperCase();

  return {
    id: String(item.id || ''),
    regionId: String(item.regionId || item.region_id || ''),
    category: String(item.category || item.categoria || 'OTRO').toUpperCase(),
    description: item.description || item.descripcion || '',
    latitude: Number(item.latitude ?? item.latitud ?? item.lat ?? 0),
    longitude: Number(item.longitude ?? item.longitud ?? item.lng ?? 0),
    status: ['RECIBIDO', 'VALIDADO', 'DERIVADO', 'DESCARTADO'].includes(normalizedStatus)
      ? normalizedStatus
      : 'RECIBIDO',
    createdAt: normalizeDateTime(item.createdAt || item.created_at || item.fechaCreacion || new Date().toISOString())
  };
}

export function withinDateRange(value, from, to) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return false;
  }

  if (from) {
    const fromDate = new Date(`${from}T00:00:00`);
    if (date < fromDate) {
      return false;
    }
  }

  if (to) {
    const toDate = new Date(`${to}T23:59:59`);
    if (date > toDate) {
      return false;
    }
  }

  return true;
}

export function distanceKm(lat1, lon1, lat2, lon2) {
  const toRad = (value) => (value * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  return 6371 * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
}

export function riskWeight(level) {
  if (level === 'CRITICO') return 4;
  if (level === 'ALTO') return 3;
  if (level === 'MEDIO') return 2;
  return 1;
}

// With an empty reports list this degrades to risk + recency weighting only.
export function calculatePriority(alert, reports) {
  const nearbyReports = reports.filter((report) => {
    if (!Number.isFinite(report.latitude) || !Number.isFinite(report.longitude)) {
      return false;
    }
    const distance = distanceKm(Number(alert.latitud), Number(alert.longitud), report.latitude, report.longitude);
    return distance <= 12;
  });

  const alertDate = new Date(alert.fechaEvento);
  const ageHours = Number.isNaN(alertDate.getTime()) ? 999 : (Date.now() - alertDate.getTime()) / 3600000;
  const recencyBonus = ageHours <= 24 ? 2 : ageHours <= 72 ? 1 : 0;
  const score = riskWeight(alert.nivelRiesgo) * 2 + Math.min(3, nearbyReports.length) + recencyBonus;

  return {
    ...alert,
    nearbyReports: nearbyReports.length,
    priorityScore: score
  };
}

// Comunal risk scores map -> sorted ALTO/CRITICO list for the operational panel.
export function toOperationalAlerts(comunalScores) {
  return Object.entries(comunalScores || {})
    .map(([comunaId, data]) => ({ comunaId, ...data }))
    .filter((item) => item.alertLevel === 'ALTO' || item.alertLevel === 'CRITICO')
    .sort((a, b) => {
      const levelDiff = (ALERT_LEVEL_ORDER[a.alertLevel] ?? 9) - (ALERT_LEVEL_ORDER[b.alertLevel] ?? 9);
      if (levelDiff !== 0) return levelDiff;
      return (b.scoreComposite ?? 0) - (a.scoreComposite ?? 0);
    });
}

// ---- Public (anonymous) mode -------------------------------------------------

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const DAY_MS = 86400000;

function pad(value, length) {
  return String(value).padStart(length, '0');
}

/** Local (browser) calendar date as YYYY-MM-DD; `now` is injectable for checks. */
export function localTodayIso(now = new Date()) {
  return `${pad(now.getFullYear(), 4)}-${pad(now.getMonth() + 1, 2)}-${pad(now.getDate(), 2)}`;
}

// Strict parse: exact YYYY-MM-DD and a real calendar date, otherwise null.
// setUTCFullYear keeps years 0-99 exact (Date.UTC would map them to 1900-1999).
function isoToUtcMs(iso) {
  if (!ISO_DATE.test(iso || '')) return null;
  const [y, m, d] = iso.split('-').map(Number);
  const date = new Date(0);
  date.setUTCFullYear(y, m - 1, d);
  const ms = date.getTime();
  if (Number.isNaN(ms)) return null;
  return utcMsToIso(ms) === iso ? ms : null;
}

function utcMsToIso(ms) {
  const date = new Date(ms);
  return `${pad(date.getUTCFullYear(), 4)}-${pad(date.getUTCMonth() + 1, 2)}-${pad(date.getUTCDate(), 2)}`;
}

/** True for a complete, real ISO date whose year is >= 2000 (the backend minimum). */
export function isCommittablePublicDate(iso) {
  const ms = isoToUtcMs(iso);
  return ms !== null && ms >= isoToUtcMs(PUBLIC_MIN_DATE);
}

/**
 * Makes a from/to pair safe for the public endpoints: real dates only, never before
 * 2000-01-01, never after the (local) today, never from > to, never a span above
 * 89 days. Whenever one bound is set the other is made explicit (to defaults to
 * today, from to `to - 29 days`), so the request never depends on the server's
 * notion of "today". Both empty stays empty (backend default 30-day window).
 * @param {string} todayIso the LOCAL date of the browser (see localTodayIso)
 * @returns {{ from: string, to: string, notice: string }} notice is a user-facing
 *   Spanish hint when something had to be adjusted, '' otherwise.
 */
export function clampPublicRange(from, to, todayIso) {
  const todayMs = isoToUtcMs(todayIso);
  const minMs = isoToUtcMs(PUBLIC_MIN_DATE);
  let fromMs = isoToUtcMs(from);
  let toMs = isoToUtcMs(to);
  const notices = [];

  if ((from && fromMs === null) || (to && toMs === null)) {
    notices.push('Fecha invalida; se ignoro.');
  }

  if (toMs !== null && todayMs !== null && toMs > todayMs) {
    toMs = todayMs;
    notices.push('La fecha "hasta" no puede ser futura.');
  }
  if (fromMs !== null && todayMs !== null && fromMs > todayMs) {
    fromMs = todayMs;
    notices.push('La fecha "desde" no puede ser futura.');
  }
  if ((fromMs !== null && fromMs < minMs) || (toMs !== null && toMs < minMs)) {
    if (fromMs !== null && fromMs < minMs) fromMs = minMs;
    if (toMs !== null && toMs < minMs) toMs = minMs;
    notices.push('La fecha minima es 01-01-2000; se ajusto el rango.');
  }

  if (fromMs === null && toMs === null) {
    return { from: '', to: '', notice: notices.join(' ') };
  }

  if (toMs === null) toMs = todayMs;
  if (fromMs === null) fromMs = Math.max(minMs, toMs - (DEFAULT_WINDOW_DAYS - 1) * DAY_MS);

  if (fromMs > toMs) {
    fromMs = toMs;
    notices.push('"Desde" no puede ser posterior a "hasta"; se ajusto el rango.');
  }
  if ((toMs - fromMs) / DAY_MS > PUBLIC_CLIENT_SPAN_DAYS) {
    fromMs = toMs - PUBLIC_CLIENT_SPAN_DAYS * DAY_MS;
    notices.push(`El periodo publico maximo es de ${PUBLIC_MAX_SPAN_DAYS} dias; se ajusto el inicio.`);
  }

  return { from: utcMsToIso(fromMs), to: utcMsToIso(toMs), notice: notices.join(' ') };
}

// dd-mm-yyyy from an ISO date string, without going through Date (avoids the
// UTC-midnight -> previous-day shift in Chilean time zones).
export function formatIsoDate(value) {
  if (!ISO_DATE.test(value || '')) return value || '-';
  const [y, m, d] = value.split('-');
  return `${d}-${m}-${y}`;
}

/**
 * Public DTO {comuna, comunaLevel, regionId, fecha, nivelRiesgo, lat, lon} ->
 * the internal alert shape used by the map/table/priority code. The public feed
 * carries no id, so a positional one is synthesized (stable for a given response).
 * `comuna` is the comuna name, or the region name when comunaLevel is false.
 */
export function normalizePublicAlert(item, index) {
  return {
    id: `pub-${index}`,
    regionId: String(item.regionId || ''),
    fechaEvento: item.fecha || '',
    nivelRiesgo: String(item.nivelRiesgo || 'BAJO').toUpperCase(),
    latitud: Number(item.lat),
    longitud: Number(item.lon),
    comuna: item.comuna || '',
    comunaLevel: item.comunaLevel === true,
    // Map popup text; only the public label, never a free-text description.
    descripcion: item.comuna || ''
  };
}

/** Public report DTO {category, subCategory, description, createdAt, photos, lat, lon}. */
export function normalizePublicReport(item, index) {
  return {
    id: `pub-report-${index}`,
    regionId: '',
    category: String(item.category || 'OTRO').toUpperCase(),
    description: item.description || '',
    latitude: Number(item.lat),
    longitude: Number(item.lon),
    status: 'VALIDADO',
    createdAt: item.createdAt || ''
  };
}
