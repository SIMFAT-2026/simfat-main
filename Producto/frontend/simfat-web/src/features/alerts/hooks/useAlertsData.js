import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { fetchComunalRiskScores } from '../../territory/services/territoryApiService';
import { REGION_OPTIONS } from '../../territory/hooks/useTerritoryLayers';
import {
  getAlertsMap,
  getCitizenReports,
  getPublicAlerts,
  getPublicCitizenReports,
  getRegions
} from '../../../services';
import {
  PUBLIC_ALERTS_CAP,
  PUBLIC_DEFAULT_REGION_ID,
  RISK_LEVELS,
  calculatePriority,
  clampPublicRange,
  localTodayIso,
  normalizePublicAlert,
  normalizePublicReport,
  normalizeReport,
  toOperationalAlerts,
  withinDateRange
} from '../utils/alertsLogic';

// Region options for anonymous mode: same hardcoded set the territory hook uses,
// so no /api/regions call (authenticated endpoint) is needed.
const PUBLIC_REGIONS = REGION_OPTIONS.map((region) => ({ id: region.id, nombre: region.label }));
const PUBLIC_REGION_IDS = new Set(PUBLIC_REGIONS.map((region) => region.id));

// Debounce for the public custom date inputs, so intermediate keystrokes
// (e.g. year 0002 while typing 2026) never reach the backend.
const PUBLIC_DATE_DEBOUNCE_MS = 400;

// Authenticated data path: unchanged requests (alerts/map, citizen reports, comunal scores).
async function fetchAuthenticatedData({ regionId, level, from, to }) {
  const [alertsData, reportData, comunalScores] = await Promise.all([
    getAlertsMap({
      regionId: regionId || undefined,
      level: level || undefined,
      from: from || undefined,
      to: to || undefined
    }),
    getCitizenReports({
      regionId: regionId || undefined
    }),
    fetchComunalRiskScores(regionId || 'biobio')
  ]);

  const normalizedReports = (Array.isArray(reportData) ? reportData : []).map(normalizeReport);
  const reportsByDate = normalizedReports.filter((item) => withinDateRange(item.createdAt, from, to));

  return {
    alerts: Array.isArray(alertsData) ? alertsData : [],
    reports: reportsByDate,
    operationalAlerts: toOperationalAlerts(comunalScores),
    secondaryFailed: false,
    scoresFailed: false
  };
}

// Anonymous data path: tokenless client and public endpoints only. Alerts are
// required (their failure is the view error); reports and comunal scores are
// complementary, so their failure degrades the view instead of breaking it.
async function fetchPublicData({ regionId, from, to }) {
  const range = clampPublicRange(from, to, localTodayIso());
  const region = regionId || PUBLIC_DEFAULT_REGION_ID;
  let secondaryFailed = false;
  let scoresFailed = false;
  const soft = (promise, fallback, onFail) =>
    promise.catch(() => {
      secondaryFailed = true;
      if (onFail) onFail();
      return fallback;
    });

  const [alertsData, reportData, comunalScores] = await Promise.all([
    getPublicAlerts({ regionId: region, from: range.from, to: range.to }),
    soft(getPublicCitizenReports({ regionId: region, from: range.from, to: range.to }), []),
    soft(fetchComunalRiskScores(region, { publicMode: true }), {}, () => {
      scoresFailed = true;
    })
  ]);

  // The public endpoint has no level filter: the hook applies it client-side over this raw list.
  return {
    alerts: alertsData.map(normalizePublicAlert),
    reports: reportData.map(normalizePublicReport),
    operationalAlerts: toOperationalAlerts(comunalScores),
    secondaryFailed,
    scoresFailed
  };
}

/**
 * Filters + data for the alerts views.
 * publicMode=true: ONLY the tokenless client and public endpoints, static region
 * options, mandatory region (defaults to biobio), window clamped to backend rules.
 * publicMode=false: same requests as before the split (authenticated client).
 */
export function useAlertsData({ publicMode = false } = {}) {
  const [searchParams, setSearchParams] = useSearchParams();
  const defaultRegionId = publicMode ? PUBLIC_DEFAULT_REGION_ID : '';
  const [regions, setRegions] = useState(publicMode ? PUBLIC_REGIONS : []);
  const [rawAlerts, setRawAlerts] = useState([]);
  const [reports, setReports] = useState([]);
  const [operationalAlerts, setOperationalAlerts] = useState([]);
  const [secondaryFailed, setSecondaryFailed] = useState(false);
  const [scoresFailed, setScoresFailed] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [filterRegionId, setFilterRegionId] = useState(() => {
    const fromUrl = searchParams.get('regionId') || '';
    if (publicMode) {
      return PUBLIC_REGION_IDS.has(fromUrl) ? fromUrl : defaultRegionId;
    }
    return fromUrl;
  });
  const [filterRiskLevel, setFilterRiskLevel] = useState(() => {
    const level = (searchParams.get('level') || '').toUpperCase();
    return RISK_LEVELS.includes(level) ? level : '';
  });
  const [filterFrom, setFilterFrom] = useState(() => searchParams.get('from') || '');
  const [filterTo, setFilterTo] = useState(() => searchParams.get('to') || '');
  // Dates coming from the URL mean a custom range, so the inputs must be visible.
  const [filterPreset, setFilterPreset] = useState(
    () => searchParams.get('preset') || (searchParams.get('from') || searchParams.get('to') ? 'custom' : '')
  );
  // Public mode: dates are debounced before they drive the query; authenticated mode uses them directly.
  const [debouncedFrom, setDebouncedFrom] = useState(filterFrom);
  const [debouncedTo, setDebouncedTo] = useState(filterTo);
  const debounceTimer = useRef(null);
  useEffect(() => {
    if (!publicMode) return undefined;
    const timer = window.setTimeout(() => {
      debounceTimer.current = null;
      setDebouncedFrom(filterFrom);
      setDebouncedTo(filterTo);
    }, PUBLIC_DATE_DEBOUNCE_MS);
    debounceTimer.current = timer;
    return () => window.clearTimeout(timer);
  }, [publicMode, filterFrom, filterTo]);

  // Discrete actions (presets, clear, region change, refresh) must not wait for the debounce:
  // cancel the pending timer and apply the given dates to the query state right away.
  const flushDebouncedDates = useCallback((from, to) => {
    if (debounceTimer.current !== null) {
      window.clearTimeout(debounceTimer.current);
      debounceTimer.current = null;
    }
    setDebouncedFrom(from);
    setDebouncedTo(to);
  }, []);
  const queryFrom = publicMode ? debouncedFrom : filterFrom;
  const queryTo = publicMode ? debouncedTo : filterTo;
  // Public mode applies the level client-side, so it must not drive the fetch.
  const fetchLevel = publicMode ? '' : filterRiskLevel;
  // Guards against out-of-order responses when filters change quickly.
  const requestSeq = useRef(0);

  const loadRegions = useCallback(async () => {
    const data = await getRegions();
    setRegions(Array.isArray(data) ? data : []);
  }, []);

  const loadAlerts = useCallback(async () => {
    const seq = ++requestSeq.current;
    setLoading(true);
    setError(null);

    try {
      const filters = { regionId: filterRegionId, level: fetchLevel, from: queryFrom, to: queryTo };
      const result = publicMode ? await fetchPublicData(filters) : await fetchAuthenticatedData(filters);
      if (seq !== requestSeq.current) return;

      setRawAlerts(result.alerts);
      setReports(result.reports);
      setOperationalAlerts(result.operationalAlerts);
      setSecondaryFailed(result.secondaryFailed);
      setScoresFailed(result.scoresFailed);
    } catch (err) {
      if (seq !== requestSeq.current) return;
      setError(err);
      if (publicMode) {
        // Do not keep a stale operational panel (previous region/period) next to the error.
        setOperationalAlerts([]);
        setScoresFailed(false);
        // Also clear the raw data so derived KPIs do not show another region's numbers.
        setRawAlerts([]);
        setReports([]);
      }
    } finally {
      if (seq === requestSeq.current) {
        setLoading(false);
      }
    }
  }, [publicMode, filterRegionId, fetchLevel, queryFrom, queryTo]);

  useEffect(() => {
    async function init() {
      try {
        // Anonymous mode has no /api/regions call: options are static.
        await Promise.all([publicMode ? Promise.resolve() : loadRegions(), loadAlerts()]);
      } catch (err) {
        setError(err);
        setLoading(false);
      }
    }

    init();
  }, [publicMode, loadRegions, loadAlerts]);

  useEffect(() => {
    const nextParams = {
      ...(filterRegionId ? { regionId: filterRegionId } : {}),
      ...(filterRiskLevel ? { level: filterRiskLevel } : {}),
      ...(filterFrom ? { from: filterFrom } : {}),
      ...(filterTo ? { to: filterTo } : {})
    };

    const nextString = new window.URLSearchParams(nextParams).toString();
    const currentString = searchParams.toString();
    if (nextString !== currentString) {
      setSearchParams(nextParams, { replace: true });
    }
  }, [filterRegionId, filterRiskLevel, filterFrom, filterTo, searchParams, setSearchParams]);

  const alerts = useMemo(
    () => (publicMode && filterRiskLevel ? rawAlerts.filter((item) => item.nivelRiesgo === filterRiskLevel) : rawAlerts),
    [publicMode, filterRiskLevel, rawAlerts]
  );
  // The backend cap applies before the client-side level filter.
  const alertsCapped = publicMode && rawAlerts.length >= PUBLIC_ALERTS_CAP;

  const regionMap = useMemo(() => {
    return regions.reduce((acc, region) => {
      acc[region.id] = region.nombre;
      return acc;
    }, {});
  }, [regions]);

  const priorityRows = useMemo(() => {
    return alerts
      .map((alert) => calculatePriority(alert, reports))
      .sort((a, b) => b.priorityScore - a.priorityScore)
      .slice(0, 5);
  }, [alerts, reports]);

  const alertInsights = useMemo(() => {
    const byCritical = alerts.filter((item) => item.nivelRiesgo === 'CRITICO').length;
    const byHigh = alerts.filter((item) => item.nivelRiesgo === 'ALTO').length;
    const validatedReports = reports.filter((item) => item.status === 'VALIDADO').length;
    const regionsWithAlerts = new Set(alerts.map((item) => item.regionId).filter(Boolean)).size;
    const topPriority = priorityRows[0] || null;

    return {
      totalAlerts: alerts.length,
      byCritical,
      byHigh,
      totalReports: reports.length,
      validatedReports,
      regionsWithAlerts,
      topPriority
    };
  }, [alerts, reports, priorityRows]);

  // Public custom range: what will really be requested (clamped) + a hint for the user.
  const rangeNotice = useMemo(
    () => (publicMode ? clampPublicRange(queryFrom, queryTo, localTodayIso()).notice : ''),
    [publicMode, queryFrom, queryTo]
  );

  // Sets raw and query dates together; in public mode also flushes the debounce.
  function commitDates(from, to) {
    setFilterFrom(from);
    setFilterTo(to);
    if (publicMode) flushDebouncedDates(from, to);
  }

  function applyPreset(preset) {
    setFilterPreset(preset);
    if (preset === 'all' || preset === 'custom' || preset === '') {
      commitDates('', '');
      return;
    }
    const now = new Date();
    const daysBack = preset === '24h' ? 1 : preset === '48h' ? 2 : 7;
    const from = new Date(now);
    from.setDate(from.getDate() - daysBack);
    // Public mode needs the local date (the clamp compares against the local today).
    commitDates(
      publicMode ? localTodayIso(from) : from.toISOString().slice(0, 10),
      publicMode ? localTodayIso(now) : now.toISOString().slice(0, 10)
    );
  }

  function clearFilters() {
    setFilterRegionId(defaultRegionId);
    setFilterRiskLevel('');
    commitDates('', '');
    setFilterPreset('');
  }

  // Public mode: a region change applies any pending date edit in the same batch (one request).
  function changeRegion(regionId) {
    if (publicMode) flushDebouncedDates(filterFrom, filterTo);
    setFilterRegionId(regionId);
  }

  // Public mode: refresh applies pending date edits first. If they differ from the query
  // state the state change itself triggers the single fetch; otherwise refetch directly.
  function refresh() {
    if (publicMode && (filterFrom !== debouncedFrom || filterTo !== debouncedTo)) {
      flushDebouncedDates(filterFrom, filterTo);
      return undefined;
    }
    return loadAlerts();
  }

  return {
    publicMode,
    regions,
    regionMap,
    alerts,
    alertsCapped,
    reports,
    operationalAlerts,
    secondaryFailed,
    scoresFailed,
    loading,
    error,
    loadAlerts: publicMode ? refresh : loadAlerts,
    filterRegionId,
    setFilterRegionId: publicMode ? changeRegion : setFilterRegionId,
    filterRiskLevel,
    setFilterRiskLevel,
    filterFrom,
    setFilterFrom,
    filterTo,
    setFilterTo,
    filterPreset,
    applyPreset,
    clearFilters,
    priorityRows,
    alertInsights,
    rangeNotice
  };
}
