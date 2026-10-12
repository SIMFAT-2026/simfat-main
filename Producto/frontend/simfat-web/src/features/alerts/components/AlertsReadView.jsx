import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import AlertBadge from '../../../components/AlertBadge';
import DataTable, { RISK_ORDER } from '../../../components/DataTable';
import EmptyState from '../../../components/EmptyState';
import ErrorMessage from '../../../components/ErrorMessage';
import FilterBar from '../../../components/FilterBar';
import LoadingSpinner from '../../../components/LoadingSpinner';
import SectionTitle from '../../../components/SectionTitle';
import { useCloseDetailsOnOutsideClick } from '../../../hooks';
import {
  PUBLIC_ALERTS_CAP,
  PUBLIC_MAX_SPAN_DAYS,
  PUBLIC_MIN_DATE,
  PUBLIC_REPORTS_CAP,
  RISK_LEVELS,
  formatDateTime,
  formatIsoDate,
  localTodayIso
} from '../utils/alertsLogic';
import AlertsOperationalMap from './AlertsOperationalMap';

const OPERATIONAL_ALERTS_VISIBLE = 10;

const PERIOD_PRESETS = [
  { key: 'all', label: 'Mostrar todo' },
  { key: '24h', label: 'Últimas 24h' },
  { key: '48h', label: 'Últimas 48h' },
  { key: '7d', label: 'Última semana' },
  { key: 'custom', label: 'Personalizado' }
];

function OperationalItem({ item, position }) {
  return (
    <li className="alerts-operational-item">
      <span className="alerts-operational-num">{position}</span>
      <strong>{item.nombreComuna || item.comunaId}</strong>
      <AlertBadge level={item.alertLevel} />
      <span className="alerts-priority-score">{item.scoreComposite != null ? `${Math.round(item.scoreComposite * 100)}/100` : '-'}</span>
    </li>
  );
}

// Label for a public alert: comuna name, or the region name when the backend could
// not attribute the event to a comuna (comunaLevel === false).
function publicAlertLabel(item, regionMap) {
  return item.comunaLevel ? item.comuna : regionMap[item.regionId] || item.comuna || item.regionId;
}

/**
 * Read-only alerts view: filters, metrics, operational panel, map, priority list and
 * table. Shared by the anonymous page (publicMode) and the authenticated page, which
 * injects its write affordances through `rowActions` and `onExportCsv`.
 *
 * @param {object} props
 * @param {ReturnType<import('../hooks/useAlertsData').useAlertsData>} props.data
 * @param {(row: object) => import('react').ReactNode} [props.rowActions] authenticated only
 * @param {() => void} [props.onExportCsv] authenticated only
 * @param {import('react').ReactNode} [props.feedback] authenticated only
 * @param {import('react').ReactNode} [props.children] rendered at the end of the page section (edit form, modals)
 */
function AlertsReadView({ data, rowActions, onExportCsv, feedback, children }) {
  useCloseDetailsOnOutsideClick();
  const [selectedAlertId, setSelectedAlertId] = useState(null);
  const {
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
    loadAlerts,
    filterRegionId,
    setFilterRegionId,
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
  } = data;

  const columns = useMemo(() => {
    if (publicMode) {
      return [
        {
          key: 'comuna',
          header: 'Comuna',
          sortable: true,
          sortValue: (row) => publicAlertLabel(row, regionMap),
          render: (row) => (
            <>
              {publicAlertLabel(row, regionMap)}
              {row.comunaLevel ? null : <small className="card-subtitle"> (nivel regional)</small>}
            </>
          )
        },
        {
          key: 'regionId',
          header: 'Región',
          sortable: true,
          sortValue: (row) => regionMap[row.regionId] || row.regionId,
          render: (row) => regionMap[row.regionId] || row.regionId
        },
        {
          key: 'fechaEvento',
          header: 'Fecha evento',
          sortable: true,
          sortValue: (row) => row.fechaEvento,
          render: (row) => formatIsoDate(row.fechaEvento)
        },
        {
          key: 'nivelRiesgo',
          header: 'Nivel de riesgo',
          sortable: true,
          sortValue: (row) => RISK_ORDER[row.nivelRiesgo] ?? 0,
          render: (row) => <AlertBadge level={row.nivelRiesgo} />
        }
      ];
    }

    return [
      {
        key: 'regionId',
        header: 'Región',
        sortable: true,
        sortValue: (row) => regionMap[row.regionId] || row.regionId,
        render: (row) => regionMap[row.regionId] || row.regionId
      },
      {
        key: 'fechaEvento',
        header: 'Fecha evento',
        sortable: true,
        sortValue: (row) => row.fechaEvento,
        render: (row) => formatDateTime(row.fechaEvento)
      },
      {
        key: 'nivelRiesgo',
        header: 'Nivel de riesgo',
        sortable: true,
        sortValue: (row) => RISK_ORDER[row.nivelRiesgo] ?? 0,
        render: (row) => <AlertBadge level={row.nivelRiesgo} />
      },
      { key: 'fuente', header: 'Fuente', sortable: true },
      { key: 'descripcion', header: 'Descripción' }
    ];
  }, [publicMode, regionMap]);

  const today = localTodayIso();

  // A refetch or level change can replace the list: drop a selection that may now point elsewhere.
  useEffect(() => {
    setSelectedAlertId(null);
  }, [alerts]);

  return (
    <section className="page-container">
      <SectionTitle
        title="Alertas"
        subtitle={
          publicMode
            ? 'Eventos de calor y priorización territorial con datos públicos (ubicación aproximada)'
            : 'Eventos de calor, priorización territorial y cruce con reportes ciudadanos'
        }
      />

      {feedback}

      <FilterBar>
        <label>
          Filtrar por región
          <select value={filterRegionId} onChange={(event) => setFilterRegionId(event.target.value)}>
            {publicMode ? null : <option value="">Todas</option>}
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

        <div className="filter-presets">
          <span className="filter-presets-label">Periodo</span>
          {PERIOD_PRESETS.map(({ key, label: presetLabel }) => {
            const isActive = filterPreset === key || (key === 'all' && filterPreset === '');
            // Anonymous "all" means the backend default window (last 30 days).
            const label = publicMode && key === 'all' ? 'Últimos 30 días' : presetLabel;
            return (
              <button
                key={key}
                type="button"
                className={`btn btn-sm btn-preset${isActive ? ' btn-preset-active' : ' btn-secondary'}`}
                aria-pressed={isActive}
                onClick={() => applyPreset(key)}
              >
                {label}
              </button>
            );
          })}
        </div>

        {filterPreset === 'custom' ? (
          <>
            <label>
              Desde
              <input
                type="date"
                value={filterFrom}
                min={publicMode ? PUBLIC_MIN_DATE : undefined}
                max={publicMode ? today : undefined}
                onChange={(event) => setFilterFrom(event.target.value)}
              />
            </label>
            <label>
              Hasta
              <input
                type="date"
                value={filterTo}
                min={publicMode ? PUBLIC_MIN_DATE : undefined}
                max={publicMode ? today : undefined}
                onChange={(event) => setFilterTo(event.target.value)}
              />
            </label>
          </>
        ) : null}

        <div className="form-actions">
          <button type="button" className="btn btn-secondary" onClick={loadAlerts} disabled={loading}>
            {loading ? 'Actualizando…' : publicMode ? 'Actualizar' : 'Actualizar cruce'}
          </button>
          <button type="button" className="btn btn-secondary" onClick={clearFilters}>
            Limpiar filtros
          </button>
          <Link className="btn btn-secondary" to={`/territorio?regionId=${filterRegionId || 'biobio'}&focus=alerts`}>
            Ver en territorio
          </Link>
        </div>
      </FilterBar>

      {publicMode && rangeNotice ? (
        <p className="card-subtitle" role="status">
          {rangeNotice}
        </p>
      ) : null}
      {publicMode ? (
        <p className="card-subtitle">
          Vista pública: muestra hasta {PUBLIC_MAX_SPAN_DAYS} días por consulta (por defecto los últimos 30) y como máximo{' '}
          {PUBLIC_ALERTS_CAP} alertas recientes. Las ubicaciones se redondean (~1 km). Inicia sesión para ver el detalle completo.
        </p>
      ) : null}
      {publicMode && !loading && !error && secondaryFailed ? (
        <p className="card-subtitle" role="status">
          No se pudieron cargar los reportes verificados o el panel de comunas; se muestran solo las alertas.
        </p>
      ) : null}

      {!loading && !(publicMode && error) ? (
        <section className="metrics-grid">
          <article className="metric-card">
            <div className="metric-label">
              Eventos registrados
              <details className="access-help metric-help">
                <summary>&#9432;</summary>
                {publicMode ? (
                  <p>Detecciones y alertas de la región seleccionada dentro del periodo elegido (máximo 90 días y 1000 eventos, los más recientes primero).</p>
                ) : (
                  <p>Total de eventos de alerta que coinciden con los filtros activos. Incluye detecciones FIRMS y alertas creadas manualmente. Sin filtros, muestra el histórico completo.</p>
                )}
              </details>
            </div>
            <strong>{alertInsights.totalAlerts}</strong>
          </article>
          <article className="metric-card">
            <div className="metric-label">
              Críticas / Altas
              <details className="access-help metric-help">
                <summary>&#9432;</summary>
                {publicMode ? (
                  <p>Eventos con nivel Crítico o Alto en la región y el periodo seleccionados. Corresponde al nivel asignado al momento de registrar la detección. No refleja el score de riesgo comunal.</p>
                ) : (
                  <p>Eventos con nivel Crítico o Alto según los filtros activos. Corresponde al nivel asignado al momento de registrar el evento (FRP para FIRMS, manual para alertas operativas). No refleja el score de riesgo comunal.</p>
                )}
              </details>
            </div>
            <strong>
              {alertInsights.byCritical} / {alertInsights.byHigh}
            </strong>
          </article>
          <article className="metric-card">
            <div className="metric-label">
              {publicMode ? 'Reportes verificados' : 'Reportes ciudadanos'}
              <details className="access-help metric-help">
                <summary>&#9432;</summary>
                {publicMode ? (
                  <p>Reportes ciudadanos ya validados por un moderador en la región y periodo consultados. Se usan para cruzar con eventos de alerta en la priorización.</p>
                ) : (
                  <p>Reportes recibidos de ciudadanos en la región filtrada. El segundo número corresponde a reportes validados por un moderador. Se usan para cruzar con eventos de alerta en la priorización operativa.</p>
                )}
              </details>
            </div>
            <strong>
              {publicMode
                ? alertInsights.validatedReports
                : `${alertInsights.totalReports} / ${alertInsights.validatedReports}`}
            </strong>
          </article>
          <article className="metric-card">
            <div className="metric-label">
              Comunas en alerta
              <details className="access-help metric-help">
                <summary>&#9432;</summary>
                <p>Comunas que escalaron a nivel Alto o Crítico según el puntaje de riesgo compuesto (FWI, FIRMS, NDMI, NDVI, reportes). Corresponde al panel de alertas operativas de abajo.</p>
              </details>
            </div>
            <strong>{operationalAlerts.length}</strong>
          </article>
          <article className="metric-card">
            <div className="metric-label">
              Mayor prioridad
              <details className="access-help metric-help">
                <summary>&#9432;</summary>
                <p>Puntaje del evento de alerta con mayor prioridad operativa. Se calcula combinando el nivel de riesgo del evento, reportes ciudadanos cercanos (radio 12 km) y la recencia del evento (bonus si ocurrió en las últimas 24 o 72 horas).</p>
              </details>
            </div>
            <strong>{alertInsights.topPriority ? alertInsights.topPriority.priorityScore : '-'}</strong>
          </article>
        </section>
      ) : null}

      {!(publicMode && error) ? (
        <article className="dashboard-card">
          <div className="card-title-row">
            <h3>Alertas operativas por comuna</h3>
            <details className="access-help">
              <summary>&#9432; Cómo funciona</summary>
              <ul>
                <li><strong>Qué muestra:</strong> comunas que escalaron a Alto o Crítico según el puntaje de riesgo compuesto. No incluye lecturas FIRMS crudas.</li>
                <li><strong>Puntaje (0–100):</strong> combina FWI (índice meteorológico de riesgo de incendio), humedad de la vegetación (NDMI), cobertura vegetal (NDVI), detecciones satelitales FIRMS y reportes ciudadanos.</li>
                <li><strong>Por qué el FWI solo puede escalar a Alto:</strong> si el FWI es 20 o más, la comuna sube a Alto independientemente del puntaje compuesto. Refleja una condición meteorológica de riesgo.</li>
                <li><strong>Crítico:</strong> detección FIRMS activa hoy, FWI &gt;= 45, o puntaje &gt;= 85/100.</li>
                <li><strong>Orden:</strong> Crítico primero, luego Alto. Dentro de cada nivel, mayor puntaje arriba.</li>
              </ul>
            </details>
          </div>
          <p className="card-subtitle">
            Comunas que escalaron a nivel Alto o Crítico según el puntaje de riesgo (FWI, NDMI, NDVI, FIRMS, reportes). No incluye
            detecciones FIRMS crudas — eso está en la tabla de eventos más abajo.
          </p>
          {publicMode && loading ? (
            <p className="card-subtitle" role="status" aria-busy="true">Cargando comunas en alerta…</p>
          ) : (
            <>
              {publicMode && scoresFailed ? (
                <EmptyState title="Panel no disponible" description="No se pudo cargar el puntaje de riesgo por comuna. Intenta actualizar más tarde." />
              ) : operationalAlerts.length === 0 ? (
                <EmptyState title="Sin comunas en nivel Alto o Crítico" description="Ninguna comuna de la región filtrada escaló de nivel." />
              ) : (
                <>
                  <ol className="alerts-operational-grid">
                    {operationalAlerts.slice(0, OPERATIONAL_ALERTS_VISIBLE).map((item, idx) => (
                      <OperationalItem key={item.comunaId} item={item} position={idx + 1} />
                    ))}
                  </ol>
                  {operationalAlerts.length > OPERATIONAL_ALERTS_VISIBLE ? (
                    <details className="alerts-see-more">
                      <summary>{operationalAlerts.length - OPERATIONAL_ALERTS_VISIBLE} comunas más en alerta</summary>
                      <ol className="alerts-operational-grid" start={OPERATIONAL_ALERTS_VISIBLE + 1}>
                        {operationalAlerts.slice(OPERATIONAL_ALERTS_VISIBLE).map((item, idx) => (
                          <OperationalItem key={item.comunaId} item={item} position={OPERATIONAL_ALERTS_VISIBLE + idx + 1} />
                        ))}
                      </ol>
                    </details>
                  ) : null}
                </>
              )}
            </>
          )}
        </article>
      ) : null}

      {!loading && !error && (alerts.length > 0 || reports.length > 0) ? (
        <div className="alerts-layout">
          <AlertsOperationalMap alerts={alerts} reports={reports} selectedAlertId={selectedAlertId} />
          <article className="dashboard-card alerts-priority-card">
            <h3>Priorización operativa</h3>
            {priorityRows.length === 0 ? (
              <EmptyState title="Sin alertas priorizables" description="Ajusta filtros para construir la priorización territorial." />
            ) : (
              <ul className="alerts-priority-list">
                {priorityRows.map((item) => (
                  <li
                    key={item.id}
                    className={`alerts-priority-item${selectedAlertId === item.id ? ' alerts-priority-item--selected' : ''}`}
                    onClick={() => setSelectedAlertId((prev) => (prev === item.id ? null : item.id))}
                  >
                    <strong>{publicMode ? publicAlertLabel(item, regionMap) : regionMap[item.regionId] || item.regionId}</strong>
                    <span>
                      <AlertBadge level={item.nivelRiesgo} />
                    </span>
                    <span>
                      {publicMode
                        ? item.comunaLevel
                          ? 'Alerta territorial'
                          : 'Alerta a nivel regional (sin comuna asignada)'
                        : item.descripcion || 'Alerta territorial sin descripción'}
                    </span>
                    <span className="alerts-priority-score">
                      Prioridad: {item.priorityScore} | Reportes cercanos (&lt;12 km): {item.nearbyReports}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </article>
        </div>
      ) : null}

      {loading ? <LoadingSpinner label="Cargando alertas…" /> : null}
      {!loading && error ? <ErrorMessage error={error} onRetry={loadAlerts} /> : null}
      {publicMode && !loading && !error && alertsCapped ? (
        <p className="card-subtitle" role="status">
          Se muestran los {PUBLIC_ALERTS_CAP} eventos más recientes del periodo; el filtro de nivel se aplica solo a esos{' '}
          {PUBLIC_ALERTS_CAP} eventos.
        </p>
      ) : null}
      {publicMode && !loading && !error && reports.length >= PUBLIC_REPORTS_CAP ? (
        <p className="card-subtitle" role="status">
          Para los conteos de reportes cercanos y la priorización se usan solo los {PUBLIC_REPORTS_CAP} reportes verificados más
          recientes del periodo.
        </p>
      ) : null}
      {!loading && !error && alerts.length === 0 ? (
        publicMode ? (
          <EmptyState
            title="Sin alertas en el periodo"
            description="No hay alertas públicas para la región y el periodo seleccionados. Prueba otra región o un periodo más amplio."
          />
        ) : (
          <EmptyState title="Sin alertas" />
        )
      ) : null}
      {!loading && !error && alerts.length > 0 ? (
        <>
          <div className="table-toolbar">
            <span className="table-toolbar-count">{alerts.length} eventos</span>
            {onExportCsv ? (
              <button type="button" className="btn btn-secondary btn-sm" onClick={onExportCsv}>
                Exportar CSV
              </button>
            ) : null}
          </div>
          <DataTable
            columns={columns}
            rows={alerts}
            rowKey="id"
            defaultSortKey="fechaEvento"
            defaultSortDir="desc"
            actions={rowActions}
          />
        </>
      ) : null}

      {children}
    </section>
  );
}

export default AlertsReadView;
