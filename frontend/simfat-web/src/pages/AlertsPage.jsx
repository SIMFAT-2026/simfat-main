import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import AlertBadge from '../components/AlertBadge';
import ConfirmModal from '../components/ConfirmModal';
import DataTable from '../components/DataTable';
import EmptyState from '../components/EmptyState';
import ErrorMessage from '../components/ErrorMessage';
import FilterBar from '../components/FilterBar';
import LoadingSpinner from '../components/LoadingSpinner';
import SectionTitle from '../components/SectionTitle';
import AlertsOperationalMap from '../features/alerts/components/AlertsOperationalMap';
import { useFeedback } from '../hooks';
import { createAlert, deleteAlert, getAlertsMap, getCitizenReports, getRegions, updateAlert } from '../services';
import { asNumberOrNull } from '../utils/data';
import { mapValidationErrors } from '../utils/errors';

const RISK_LEVELS = ['BAJO', 'MEDIO', 'ALTO', 'CRITICO'];

const initialForm = {
  regionId: '',
  fechaEvento: '',
  nivelRiesgo: 'BAJO',
  latitud: '',
  longitud: '',
  fuente: '',
  descripcion: ''
};

function normalizeDateTime(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toISOString();
}

function formatDateTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value || '-';
  }
  return date.toLocaleString('es-CL');
}

function normalizeReport(item) {
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

function withinDateRange(value, from, to) {
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

function distanceKm(lat1, lon1, lat2, lon2) {
  const toRad = (value) => (value * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  return 6371 * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
}

function riskWeight(level) {
  if (level === 'CRITICO') return 4;
  if (level === 'ALTO') return 3;
  if (level === 'MEDIO') return 2;
  return 1;
}

function calculatePriority(alert, reports) {
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

function AlertsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [regions, setRegions] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [form, setForm] = useState(initialForm);
  const [editingId, setEditingId] = useState('');
  const [validationErrors, setValidationErrors] = useState({});
  const [filterRegionId, setFilterRegionId] = useState(() => searchParams.get('regionId') || '');
  const [filterRiskLevel, setFilterRiskLevel] = useState(() => {
    const level = (searchParams.get('level') || '').toUpperCase();
    return RISK_LEVELS.includes(level) ? level : '';
  });
  const [filterFrom, setFilterFrom] = useState(() => searchParams.get('from') || '');
  const [filterTo, setFilterTo] = useState(() => searchParams.get('to') || '');
  const [deleteId, setDeleteId] = useState('');
  const feedback = useFeedback();

  const loadRegions = useCallback(async () => {
    const data = await getRegions();
    setRegions(Array.isArray(data) ? data : []);
  }, []);

  const loadAlerts = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const [alertsData, reportData] = await Promise.all([
        getAlertsMap({
          regionId: filterRegionId || undefined,
          level: filterRiskLevel || undefined,
          from: filterFrom || undefined,
          to: filterTo || undefined
        }),
        getCitizenReports({
          regionId: filterRegionId || undefined
        })
      ]);

      const normalizedReports = (Array.isArray(reportData) ? reportData : []).map(normalizeReport);
      const reportsByDate = normalizedReports.filter((item) => withinDateRange(item.createdAt, filterFrom, filterTo));

      setAlerts(Array.isArray(alertsData) ? alertsData : []);
      setReports(reportsByDate);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [filterRegionId, filterRiskLevel, filterFrom, filterTo]);

  useEffect(() => {
    async function init() {
      try {
        await Promise.all([loadRegions(), loadAlerts()]);
      } catch (err) {
        setError(err);
        setLoading(false);
      }
    }

    init();
  }, [loadRegions, loadAlerts]);

  useEffect(() => {
    loadAlerts();
  }, [loadAlerts]);

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

  const columns = useMemo(
    () => [
      { key: 'regionId', header: 'Region', render: (row) => regionMap[row.regionId] || row.regionId },
      { key: 'fechaEvento', header: 'Fecha evento', render: (row) => formatDateTime(row.fechaEvento) },
      { key: 'nivelRiesgo', header: 'Nivel riesgo', render: (row) => <AlertBadge level={row.nivelRiesgo} /> },
      { key: 'latitud', header: 'Latitud' },
      { key: 'longitud', header: 'Longitud' },
      { key: 'fuente', header: 'Fuente' },
      { key: 'descripcion', header: 'Descripcion' }
    ],
    [regionMap]
  );

  function onInputChange(event) {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    setValidationErrors((prev) => ({ ...prev, [name]: '' }));
  }

  function resetForm() {
    setForm(initialForm);
    setEditingId('');
    setValidationErrors({});
  }

  function startEdit(alert) {
    setEditingId(alert.id);
    setForm({
      regionId: alert.regionId || '',
      fechaEvento: alert.fechaEvento ? String(alert.fechaEvento).slice(0, 16) : '',
      nivelRiesgo: alert.nivelRiesgo || 'BAJO',
      latitud: String(alert.latitud || ''),
      longitud: String(alert.longitud || ''),
      fuente: alert.fuente || '',
      descripcion: alert.descripcion || ''
    });
    setValidationErrors({});
    feedback.clear();
  }

  async function onSubmit(event) {
    event.preventDefault();
    feedback.clear();
    setValidationErrors({});

    const payload = {
      regionId: form.regionId,
      fechaEvento: form.fechaEvento ? new Date(form.fechaEvento).toISOString() : null,
      nivelRiesgo: form.nivelRiesgo,
      latitud: asNumberOrNull(form.latitud),
      longitud: asNumberOrNull(form.longitud),
      fuente: form.fuente.trim(),
      descripcion: form.descripcion.trim()
    };

    try {
      if (editingId) {
        await updateAlert(editingId, payload);
        feedback.showSuccess('Alerta actualizada correctamente.');
      } else {
        await createAlert(payload);
        feedback.showSuccess('Alerta creada correctamente.');
      }

      resetForm();
      await loadAlerts();
    } catch (err) {
      setValidationErrors(mapValidationErrors(err.validationErrors));
      feedback.showError(err.message);
    }
  }

  async function confirmDelete() {
    if (!deleteId) {
      return;
    }

    try {
      await deleteAlert(deleteId);
      feedback.showSuccess('Alerta eliminada correctamente.');
      setDeleteId('');
      await loadAlerts();
    } catch (err) {
      feedback.showError(err.message);
      setDeleteId('');
    }
  }

  function clearFilters() {
    setFilterRegionId('');
    setFilterRiskLevel('');
    setFilterFrom('');
    setFilterTo('');
  }

  return (
    <section className="page-container">
      <SectionTitle title="Alertas" subtitle="Eventos de calor, priorizacion territorial y cruce con reportes ciudadanos" />

      {feedback.message ? <p className={`feedback feedback-${feedback.type}`}>{feedback.message}</p> : null}

      <FilterBar>
        <label>
          Filtrar por region
          <select value={filterRegionId} onChange={(event) => setFilterRegionId(event.target.value)}>
            <option value="">Todas</option>
            {regions.map((region) => (
              <option key={region.id} value={region.id}>
                {region.nombre}
              </option>
            ))}
          </select>
        </label>

        <label>
          Nivel de riesgo
          <select value={filterRiskLevel} onChange={(event) => setFilterRiskLevel(event.target.value)}>
            <option value="">Todos</option>
            {RISK_LEVELS.map((level) => (
              <option key={level} value={level}>
                {level}
              </option>
            ))}
          </select>
        </label>

        <label>
          Desde
          <input type="date" value={filterFrom} onChange={(event) => setFilterFrom(event.target.value)} />
        </label>

        <label>
          Hasta
          <input type="date" value={filterTo} onChange={(event) => setFilterTo(event.target.value)} />
        </label>

        <div className="form-actions">
          <button type="button" className="btn btn-secondary" onClick={loadAlerts} disabled={loading}>
            {loading ? 'Actualizando...' : 'Actualizar cruce'}
          </button>
          <button type="button" className="btn btn-secondary" onClick={clearFilters}>
            Limpiar filtros
          </button>
          <Link className="btn btn-secondary" to={`/territorio?regionId=${filterRegionId || 'biobio'}&focus=alerts`}>
            Ver en territorio
          </Link>
        </div>
      </FilterBar>

      {!loading ? (
        <section className="metrics-grid">
          <article className="metric-card">
            <p>Alertas filtradas</p>
            <strong>{alertInsights.totalAlerts}</strong>
          </article>
          <article className="metric-card">
            <p>Criticas / Altas</p>
            <strong>
              {alertInsights.byCritical} / {alertInsights.byHigh}
            </strong>
          </article>
          <article className="metric-card">
            <p>Reportes (total / validados)</p>
            <strong>
              {alertInsights.totalReports} / {alertInsights.validatedReports}
            </strong>
          </article>
          <article className="metric-card">
            <p>Regiones con alerta</p>
            <strong>{alertInsights.regionsWithAlerts}</strong>
          </article>
          <article className="metric-card">
            <p>Mayor prioridad</p>
            <strong>{alertInsights.topPriority ? alertInsights.topPriority.priorityScore : '-'}</strong>
          </article>
        </section>
      ) : null}

      {!loading && !error && (alerts.length > 0 || reports.length > 0) ? (
        <div className="alerts-layout">
          <AlertsOperationalMap alerts={alerts} reports={reports} />
          <article className="dashboard-card alerts-priority-card">
            <h3>Priorizacion operativa</h3>
            {priorityRows.length === 0 ? (
              <EmptyState title="Sin alertas priorizables" description="Ajusta filtros para construir priorizacion territorial." />
            ) : (
              <ul className="alerts-priority-list">
                {priorityRows.map((item) => (
                  <li key={item.id}>
                    <strong>{regionMap[item.regionId] || item.regionId}</strong>
                    <span>
                      <AlertBadge level={item.nivelRiesgo} />
                    </span>
                    <span>{item.descripcion || 'Alerta territorial sin descripcion'}</span>
                    <span className="alerts-priority-score">
                      Score: {item.priorityScore} | Reportes cercanos (&lt;12 km): {item.nearbyReports}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </article>
        </div>
      ) : null}

      <form className="form-grid" onSubmit={onSubmit}>
        <label>
          Region
          <select name="regionId" value={form.regionId} onChange={onInputChange} required>
            <option value="">Seleccione una region</option>
            {regions.map((region) => (
              <option key={region.id} value={region.id}>
                {region.nombre}
              </option>
            ))}
          </select>
          {validationErrors.regionId ? <small className="field-error">{validationErrors.regionId}</small> : null}
        </label>

        <label>
          Fecha evento
          <input type="datetime-local" name="fechaEvento" value={form.fechaEvento} onChange={onInputChange} required />
          {validationErrors.fechaEvento ? <small className="field-error">{validationErrors.fechaEvento}</small> : null}
        </label>

        <label>
          Nivel riesgo
          <select name="nivelRiesgo" value={form.nivelRiesgo} onChange={onInputChange} required>
            {RISK_LEVELS.map((level) => (
              <option key={level} value={level}>
                {level}
              </option>
            ))}
          </select>
          {validationErrors.nivelRiesgo ? <small className="field-error">{validationErrors.nivelRiesgo}</small> : null}
        </label>

        <label>
          Latitud
          <input name="latitud" type="number" step="0.000001" value={form.latitud} onChange={onInputChange} required />
          {validationErrors.latitud ? <small className="field-error">{validationErrors.latitud}</small> : null}
        </label>

        <label>
          Longitud
          <input name="longitud" type="number" step="0.000001" value={form.longitud} onChange={onInputChange} required />
          {validationErrors.longitud ? <small className="field-error">{validationErrors.longitud}</small> : null}
        </label>

        <label>
          Fuente
          <input name="fuente" value={form.fuente} onChange={onInputChange} required />
          {validationErrors.fuente ? <small className="field-error">{validationErrors.fuente}</small> : null}
        </label>

        <label className="full-width">
          Descripcion
          <textarea name="descripcion" value={form.descripcion} onChange={onInputChange} rows={3} />
          {validationErrors.descripcion ? <small className="field-error">{validationErrors.descripcion}</small> : null}
        </label>

        <div className="form-actions">
          <button className="btn" type="submit">
            {editingId ? 'Actualizar' : 'Crear'}
          </button>
          {editingId ? (
            <button type="button" className="btn btn-secondary" onClick={resetForm}>
              Cancelar edicion
            </button>
          ) : null}
        </div>
      </form>

      {loading ? <LoadingSpinner label="Cargando alertas..." /> : null}
      {!loading && error ? <ErrorMessage error={error} onRetry={loadAlerts} /> : null}
      {!loading && !error && alerts.length === 0 ? <EmptyState title="Sin alertas" /> : null}
      {!loading && !error && alerts.length > 0 ? (
        <DataTable
          columns={columns}
          rows={alerts}
          rowKey="id"
          actions={(row) => (
            <div className="row-actions">
              <button type="button" className="btn btn-secondary" onClick={() => startEdit(row)}>
                Editar
              </button>
              <button type="button" className="btn btn-danger" onClick={() => setDeleteId(row.id)}>
                Eliminar
              </button>
            </div>
          )}
        />
      ) : null}

      <ConfirmModal
        isOpen={Boolean(deleteId)}
        title="Eliminar alerta"
        message="Confirma la eliminacion del evento de alerta."
        confirmLabel="Eliminar"
        onConfirm={confirmDelete}
        onCancel={() => setDeleteId('')}
      />
    </section>
  );
}

export default AlertsPage;
