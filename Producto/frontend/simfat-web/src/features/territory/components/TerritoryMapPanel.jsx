import { useEffect, useState, useCallback, memo } from 'react';
import L from 'leaflet';
import ComunaRiskPanel from './ComunaRiskPanel';
import { GeoJSON, MapContainer, TileLayer, useMap } from 'react-leaflet';
import marker2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';
import EmptyState from '../../../components/EmptyState';
import ErrorMessage from '../../../components/ErrorMessage';
import LoadingSpinner from '../../../components/LoadingSpinner';

// LocalDateTime de Java llega sin 'Z' — JS lo interpreta como hora local del browser.
// Esta utilidad fuerza UTC antes de convertir a Santiago para evitar doble conversión.
function parseUtcDate(str, opts = {}) {
  if (!str) return '—';
  const utc = /Z|[+-]\d{2}:?\d{2}$/.test(str) ? str : str + 'Z';
  return new Date(utc).toLocaleString('es-CL', {
    timeZone: 'America/Santiago',
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
    ...opts
  });
}

delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: marker2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow
});

const INDICATOR_COLORS = {
  NDVI: '#16a34a',
  NDMI: '#0ea5e9',
  LOSS: '#f97316',
  ALERTS: '#dc2626',
  FIRMS: '#ff4500',
  REPORTS: '#7c3aed',
  RISK_SCORE: '#b45309'
};

const ALERT_LEVEL_CONFIG = {
  NORMAL:     { color: '#16a34a', bg: '#dcfce7', label: 'Normal',     fill: '#22c55e' },
  PREVENTIVO: { color: '#ca8a04', bg: '#fef9c3', label: 'Preventivo', fill: '#eab308' },
  ALTO:       { color: '#ea580c', bg: '#ffedd5', label: 'Alto',       fill: '#f97316' },
  CRITICO:    { color: '#dc2626', bg: '#fee2e2', label: 'Critico',    fill: '#ef4444' }
};

function comunaBaseStyle(score) {
  const level = score?.alertLevel || 'NORMAL';
  const cfg = ALERT_LEVEL_CONFIG[level] || ALERT_LEVEL_CONFIG.NORMAL;
  return {
    fillColor: cfg.fill,
    fillOpacity: score ? 0.30 : 0.10,
    color: '#334155',
    weight: 0.8,
    opacity: 0.7
  };
}

const ComunaChoropleth = memo(function ComunaChoropleth({ geoJson, comunalScores, onComunaHover, onComunaHoverEnd, onComunaClick }) {
  if (!geoJson || !geoJson.features) return null;

  return (
    <GeoJSON
      key="choropleth"
      data={geoJson}
      style={(feature) => {
        const score = comunalScores?.[feature?.properties?.comunaId];
        return comunaBaseStyle(score);
      }}
      onEachFeature={(feature, layer) => {
        const comunaId = feature?.properties?.comunaId;
        const nombre = feature?.properties?.nombre || comunaId;
        const score = comunalScores?.[comunaId];

        layer.on('mouseover', (e) => {
          layer.setStyle({ fillOpacity: 0.68, weight: 1.4, color: '#475569' });
          layer.bringToFront();
          onComunaHover?.(comunaId, nombre, score, e.containerPoint);
        });

        layer.on('mouseout', () => {
          layer.setStyle(comunaBaseStyle(score));
          onComunaHoverEnd?.();
        });

        layer.on('click', () => {
          onComunaClick?.(comunaId, nombre, score);
        });
      }}
    />
  );
});

function FitRegionBounds({ bounds }) {
  const map = useMap();

  useEffect(() => {
    if (Array.isArray(bounds) && bounds.length === 2) {
      map.fitBounds(bounds, { padding: [20, 20], maxZoom: 10 });
    }
  }, [bounds, map]);

  return null;
}

function featureLabel(feature) {
  const props = feature?.properties || {};
  return props.label || props.name || props.indicator || 'Punto territorial';
}

function featureMeta(feature) {
  const props = feature?.properties || {};

  if (props.indicator === 'FIRMS') {
    const frp = props.frp != null ? `FRP: ${Number(props.frp).toFixed(1)} MW` : '';
    const conf = props.confidence === 'h' ? 'Alta confianza' : props.confidence === 'n' ? 'Confianza nominal' : '';
    const time = props.acquiredAt ? `Detectado: ${parseUtcDate(props.acquiredAt)}` : '';
    return [frp, conf, time].filter(Boolean).join(' | ');
  }

  if (props.value !== undefined && props.value !== null) {
    return `Valor: ${Number(props.value).toFixed(3)}`;
  }
  if (props.hectares !== undefined && props.hectares !== null) {
    return `Hectareas: ${props.hectares}`;
  }
  if (props.level) {
    return `Nivel: ${props.level}`;
  }
  if (props.category) {
    return `Categoria: ${props.category}`;
  }
  return '';
}

function toPointStyle(indicator, feature) {
  const frp = feature?.properties?.frp;
  const confidence = feature?.properties?.confidence;
  let radius = indicator === 'ALERTS' ? 8 : 7;

  if (indicator === 'FIRMS') {
    radius = frp ? Math.min(6 + frp / 15, 16) : 8;
    const fillColor = confidence === 'h' ? '#dc2626' : '#f97316';
    return { radius, fillColor, color: '#7f1d1d', weight: 1.5, opacity: 0.9, fillOpacity: 0.85 };
  }

  return {
    radius,
    fillColor: INDICATOR_COLORS[indicator] || '#64748b',
    color: '#0f172a',
    weight: 1,
    opacity: 0.85,
    fillOpacity: 0.8
  };
}

const COMPONENT_LABELS = {
  fwi: 'FWI meteorológico',
  ndmi: 'Humedad vegetación',
  firms: 'Focos activos',
  loss: 'Cobertura forestal',
  ndvi: 'Índice vegetación',
  reports: 'Reportes'
};

function RiskScoreBadge({ regionData }) {
  const detail = regionData?.riskScore;
  const layerFeature = regionData?.layers?.RISK_SCORE?.features?.[0];

  const score = detail?.scoreComposite ?? layerFeature?.properties?.score ?? null;
  const alertLevel = detail?.alertLevel || layerFeature?.properties?.alertLevel || 'NORMAL';
  const components = detail?.components || null;

  if (score === null && !alertLevel) return null;

  const level = alertLevel || 'NORMAL';
  const config = ALERT_LEVEL_CONFIG[level] || ALERT_LEVEL_CONFIG.NORMAL;
  const scoreDisplay = typeof score === 'number' ? (score * 100).toFixed(0) : '—';

  return (
    <div className="risk-score-badge" style={{ borderColor: config.color, backgroundColor: config.bg }}>
      <div className="risk-score-main">
        <span className="risk-score-label">Nivel de riesgo</span>
        <span className="risk-score-level" style={{ color: config.color }}>{config.label}</span>
        <span className="risk-score-value" style={{ color: config.color }}>{scoreDisplay}<small>/100</small></span>
      </div>
      {components && (
        <div className="risk-score-breakdown">
          {Object.entries(components).map(([key, comp]) => {
            const pct = typeof comp?.score === 'number' ? (comp.score * 100).toFixed(0) : '—';
            return (
              <div key={key} className="risk-score-component">
                <span className="risk-component-label">{COMPONENT_LABELS[key] || key}</span>
                <div className="risk-component-bar-wrap">
                  <div
                    className="risk-component-bar"
                    style={{ width: `${Math.min(comp?.score * 100 || 0, 100)}%`, backgroundColor: config.color }}
                  />
                </div>
                <span className="risk-component-value">{pct}</span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

const REPORT_CATEGORY_LABELS = {
  HUMO: 'Humo',
  FOCO: 'Foco de incendio',
  INCENDIO: 'Incendio activo',
  OTRO: 'Otro'
};
const REPORT_CATEGORY_COLORS = {
  HUMO: '#94a3b8',
  FOCO: '#f97316',
  INCENDIO: '#dc2626',
  OTRO: '#64748b'
};
const REPORT_STATUS_LABELS = {
  RECIBIDO: 'Recibido',
  VALIDADO: 'Validado',
  DERIVADO: 'Derivado',
  DESCARTADO: 'Descartado'
};

function ReportCard({ report, pos, onClose }) {
  if (!report || !pos) return null;
  const props = report.properties || {};
  const category = props.category || 'OTRO';
  const color = REPORT_CATEGORY_COLORS[category] || '#64748b';
  return (
    <div className="report-card" style={{ left: pos.x + 14, top: pos.y - 10 }}>
      <div className="report-card-header">
        <span className="report-card-category" style={{ color }}>{REPORT_CATEGORY_LABELS[category] || category}</span>
        <button type="button" className="panel-close" onClick={onClose} aria-label="Cerrar">×</button>
      </div>
      {props.description && <p className="report-card-desc">{props.description}</p>}
      <div className="report-card-meta">
        {props.status && (
          <span className="report-card-status">{REPORT_STATUS_LABELS[props.status] || props.status}</span>
        )}
        {props.createdAt && (
          <span className="report-card-time">{parseUtcDate(props.createdAt)}</span>
        )}
      </div>
    </div>
  );
}

function indicatorCount(regionData, indicator) {
  return regionData?.layers?.[indicator]?.features?.length || 0;
}

function ComunaTooltip({ comunaId, nombre, score, pos }) {
  if (!comunaId || !pos) return null;
  const level = ALERT_LEVEL_CONFIG[score?.alertLevel] || ALERT_LEVEL_CONFIG.NORMAL;
  const pct = typeof score?.scoreComposite === 'number' ? (score.scoreComposite * 100).toFixed(0) : null;
  const displayNombre = nombre || score?.nombreComuna || comunaId;
  const mode = score?.mode;

  const components = score?.components || {};
  const componentEntries = Object.entries(components)
    .filter(([, v]) => v != null && v > 0)
    .sort(([, a], [, b]) => b - a)
    .slice(0, 2);

  return (
    <div
      className="comuna-tooltip"
      style={{ left: pos.x + 12, top: pos.y - 8 }}
    >
      <div className="comuna-tooltip-header">
        <span className="comuna-tooltip-nombre">{displayNombre}</span>
        {mode && (
          <span className={`comuna-mode-badge ${mode === 'ENHANCED' ? 'enhanced' : 'standard'}`}>
            {mode}
          </span>
        )}
      </div>
      {score ? (
        <>
          <div className="comuna-tooltip-level" style={{ color: level.color }}>
            {level.label}
          </div>
          <div className="comuna-tooltip-score-row">
            <div className="comuna-tooltip-bar-wrap">
              <div
                className="comuna-tooltip-bar"
                style={{ width: `${pct}%`, backgroundColor: level.color }}
              />
            </div>
            <span className="comuna-tooltip-pct" style={{ color: level.color }}>{pct}<small>/100</small></span>
          </div>
        </>
      ) : (
        <div className="comuna-tooltip-level" style={{ color: '#64748b' }}>Sin datos de riesgo aún</div>
      )}
      {componentEntries.length > 0 && (
        <div className="comuna-tooltip-components">
          {componentEntries.map(([key, val]) => (
            <span key={key} className="comuna-tooltip-comp-chip">
              {COMPONENT_LABELS[key] || key}: {(val * 100).toFixed(0)}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

function TerritoryMapPanel({
  regionOptions,
  selectedRegionId,
  setSelectedRegionId,
  visibleIndicators,
  toggleIndicator,
  regionData,
  loading,
  refreshing,
  error,
  onRetry
}) {
  const comunalGeoJson = regionData?.comunalGeoJson || null;
  const comunalScores = regionData?.comunalScores || null;

  const [hoveredComuna, setHoveredComuna] = useState(null);
  const [tooltipPos, setTooltipPos] = useState(null);
  const [selectedComuna, setSelectedComuna] = useState(null);
  const [selectedReport, setSelectedReport] = useState(null);
  const [reportPos, setReportPos] = useState(null);

  const handleComunaHover = useCallback((comunaId, nombre, score, containerPoint) => {
    setHoveredComuna({ comunaId, nombre, score });
    setTooltipPos({ x: containerPoint.x, y: containerPoint.y });
  }, []);

  const handleComunaHoverEnd = useCallback(() => {
    setHoveredComuna(null);
    setTooltipPos(null);
  }, []);

  const handleComunaClick = useCallback((comunaId, nombre, score) => {
    setSelectedComuna((prev) => prev?.comunaId === comunaId ? null : { comunaId, nombre, score });
  }, []);

  return (
    <article className="dashboard-card territory-map-card">
      <div className="territory-header">
        <h3>Mapa territorial interactivo</h3>
        <button type="button" className="btn btn-secondary" onClick={onRetry} disabled={loading || refreshing}>
          {refreshing ? 'Actualizando...' : 'Actualizar capas'}
        </button>
      </div>

      {regionData && visibleIndicators.includes('RISK_SCORE') && <RiskScoreBadge regionData={regionData} />}

      <div className="filter-bar territory-controls">
        <label>
          Region
          <select value={selectedRegionId} onChange={(event) => setSelectedRegionId(event.target.value)}>
            {regionOptions.map((region) => (
              <option key={region.id} value={region.id}>
                {region.label}
              </option>
            ))}
          </select>
        </label>

        <div className="territory-layer-toggles">
          {['RISK_SCORE', 'FIRMS', 'NDVI', 'NDMI', 'LOSS', 'ALERTS', 'REPORTS'].map((indicator) => (
            <label key={indicator} className="territory-toggle">
              <input
                type="checkbox"
                checked={visibleIndicators.includes(indicator)}
                onChange={() => toggleIndicator(indicator)}
              />
              <span>{indicator}</span>
            </label>
          ))}
        </div>
      </div>

      {loading ? <LoadingSpinner label="Cargando capas territoriales..." /> : null}
      {!loading && error ? <ErrorMessage error={{ message: error }} onRetry={onRetry} /> : null}
      {!loading && !error && !regionData ? (
        <EmptyState title="Sin datos territoriales" description="No hay capas para la region seleccionada." />
      ) : null}

      {!loading && !error && regionData ? (
        <div className="territory-map-wrapper">
          <MapContainer
            key={regionData.regionId}
            className="territory-map"
            center={regionData.center}
            zoom={regionData.zoom}
            scrollWheelZoom
          >
            <TileLayer
              attribution="&copy; OpenStreetMap contributors"
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <FitRegionBounds bounds={regionData.bounds} />

            {comunalGeoJson && (
              <ComunaChoropleth
                geoJson={comunalGeoJson}
                comunalScores={comunalScores}
                onComunaHover={handleComunaHover}
                onComunaHoverEnd={handleComunaHoverEnd}
                onComunaClick={handleComunaClick}
              />
            )}

            {visibleIndicators.filter((i) => i !== 'RISK_SCORE' && i !== 'REPORTS').map((indicator) => (
              <GeoJSON
                key={`${regionData.regionId}-${indicator}`}
                data={regionData.layers[indicator]}
                pointToLayer={(feature, latlng) => L.circleMarker(latlng, toPointStyle(indicator, feature))}
                onEachFeature={(feature, layer) => {
                  const label = featureLabel(feature);
                  const meta = featureMeta(feature);
                  layer.bindPopup(`${label}${meta ? ` | ${meta}` : ''}`);
                }}
              />
            ))}

            {visibleIndicators.includes('REPORTS') && regionData.layers.REPORTS && (
              <GeoJSON
                key={`${regionData.regionId}-REPORTS`}
                data={regionData.layers.REPORTS}
                pointToLayer={(feature, latlng) => L.circleMarker(latlng, toPointStyle('REPORTS', feature))}
                onEachFeature={(feature, layer) => {
                  layer.on('click', (e) => {
                    setSelectedReport(feature);
                    setReportPos({ x: e.containerPoint.x, y: e.containerPoint.y });
                  });
                }}
              />
            )}
          </MapContainer>

          {selectedReport && (
            <ReportCard
              report={selectedReport}
              pos={reportPos}
              onClose={() => { setSelectedReport(null); setReportPos(null); }}
            />
          )}

          {hoveredComuna && (
            <ComunaTooltip
              comunaId={hoveredComuna.comunaId}
              nombre={hoveredComuna.nombre}
              score={hoveredComuna.score}
              pos={tooltipPos}
            />
          )}

          {selectedComuna ? (
            <ComunaRiskPanel
              comunaId={selectedComuna.comunaId}
              score={selectedComuna.score}
              onClose={() => setSelectedComuna(null)}
              canSync={false}
            />
          ) : (
            <div className="territory-legend">
              <h4>Leyenda de capas</h4>
              <ul>
                {['FIRMS', 'NDVI', 'NDMI', 'LOSS', 'ALERTS', 'REPORTS'].map((indicator) => (
                  <li key={indicator}>
                    <span
                      className="territory-color-dot"
                      style={{ backgroundColor: INDICATOR_COLORS[indicator] || '#64748b' }}
                    />
                    <span>{indicator === 'FIRMS' ? 'Focos' : indicator}</span>
                    <strong>{indicatorCount(regionData, indicator)}</strong>
                  </li>
                ))}
              </ul>
              <p className="territory-meta">
                Fuente: {regionData.source === 'backend' ? 'Backend' : 'Mock local'} | Ultima actualizacion:{' '}
                {parseUtcDate(regionData.generatedAt)}
              </p>
            </div>
          )}
        </div>
      ) : null}
    </article>
  );
}

export default TerritoryMapPanel;
