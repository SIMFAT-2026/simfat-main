import { useEffect, useRef, useState, useCallback, useMemo, memo } from 'react';
import { createPortal } from 'react-dom';
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
  RISK_SCORE: '#b45309',
  WIND: '#0891b2',
  HUMIDITY: '#2563eb',
  AIR_TEMP: '#dc2626',
  SOIL_TEMP: '#b45309'
};

// Per-variable color scales (DEC-A): each climate indicator gets its own
// bins/ramp tuned for fire-risk relevance, rather than a shared generic scale.
// `bins` are ascending upper-bounds; the last entry's `max` is Infinity.
const CLIMATE_SCALES = {
  // Wind speed (km/h): higher wind = faster fire spread.
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
  // Relative humidity (%): LOWER humidity = HIGHER fire risk, so the ramp
  // is inverted (most alarming color for the lowest band).
  HUMIDITY: {
    unit: '%',
    label: 'Humedad relativa',
    bins: [
      { max: 20, color: '#dc2626', label: '< 20 (crítico)' },
      { max: 30, color: '#f97316', label: '20-30 (bajo)' },
      { max: 50, color: '#facc15', label: '30-50 (moderado)' },
      { max: 70, color: '#86efac', label: '50-70 (normal)' },
      { max: Infinity, color: '#16a34a', label: '> 70 (alto)' }
    ]
  },
  // Air temperature max (°C): finer bins in the fire-relevant high range.
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
  // Soil temperature (°C): typically narrower variance than air temp.
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

const CLIMATE_INDICATORS = ['WIND', 'HUMIDITY', 'AIR_TEMP', 'SOIL_TEMP'];
const NEUTRAL_FILL = '#cbd5e1';

function climateColorForValue(indicator, value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return NEUTRAL_FILL;
  }
  const scale = CLIMATE_SCALES[indicator];
  if (!scale) return NEUTRAL_FILL;
  const numeric = Number(value);
  const bin = scale.bins.find((b) => numeric < b.max);
  return bin ? bin.color : scale.bins[scale.bins.length - 1].color;
}

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

// Renders a climate layer (WIND/HUMIDITY/AIR_TEMP/SOIL_TEMP) as a choropleth
// by joining the backend's per-comuna value map onto the existing comuna
// GeoJSON polygons (mirrors ComunaChoropleth's join with comunalScores).
// Comunas without data render with a neutral fill instead of breaking.
const ClimateChoropleth = memo(function ClimateChoropleth({ geoJson, indicator, valueMap }) {
  if (!geoJson || !geoJson.features) return null;

  const values = valueMap?.values || {};
  const unit = valueMap?.unit || CLIMATE_SCALES[indicator]?.unit || '';

  return (
    <GeoJSON
      key={`climate-${indicator}`}
      data={geoJson}
      style={(feature) => {
        const comunaId = feature?.properties?.comunaId;
        const entry = values[comunaId];
        return {
          fillColor: climateColorForValue(indicator, entry?.value),
          fillOpacity: entry ? 0.55 : 0.12,
          color: '#334155',
          weight: 0.8,
          opacity: 0.7
        };
      }}
      onEachFeature={(feature, layer) => {
        const comunaId = feature?.properties?.comunaId;
        const nombre = feature?.properties?.nombre || comunaId;
        const entry = values[comunaId];
        const valueLabel = entry?.value !== undefined && entry?.value !== null
          ? `${Number(entry.value).toFixed(1)} ${entry.unit || unit}`
          : 'Sin datos';
        layer.bindTooltip(`${nombre}: ${valueLabel}`, { sticky: true });
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
  fwi:     'FWI meteorológico',
  ndmi:    'Humedad vegetación (NDMI)',
  firms:   'Focos activos',
  loss:    'Cobertura forestal',
  ndvi:    'Índice vegetación (NDVI)',
  reports: 'Reportes'
};

function IndexInfo({ info, label }) {
  const [tooltipStyle, setTooltipStyle] = useState(null);
  const btnRef = useRef(null);

  function handleToggle(e) {
    e.stopPropagation();
    if (tooltipStyle) {
      setTooltipStyle(null);
      return;
    }
    const rect = btnRef.current.getBoundingClientRect();
    setTooltipStyle({
      position: 'fixed',
      top: `${rect.top + rect.height / 2}px`,
      left: `${rect.right + 8}px`,
      transform: 'translateY(-50%)',
    });
  }

  return (
    <span className="comp-info-wrap">
      <button
        ref={btnRef}
        type="button"
        className="comp-info-icon"
        aria-label={`Info sobre ${label}`}
        onClick={handleToggle}
      >ⓘ</button>
      {tooltipStyle && createPortal(
        <span className="comp-info-tooltip" style={tooltipStyle}>
          {info}
          <button
            type="button"
            className="comp-info-close"
            onClick={(e) => { e.stopPropagation(); setTooltipStyle(null); }}
          >×</button>
        </span>,
        document.body
      )}
    </span>
  );
}

const COMPONENT_INFO = {
  fwi:     'Índice de Peligro de Incendio (FWI). Escala 0–100+:\n• 0–11: Bajo\n• 11–21: Moderado\n• 21–38: Alto\n• 38–50: Muy alto\n• >50: Extremo',
  ndmi:    'NDMI — Humedad de la vegetación. Rango -1 a 1:\n• >0.1: Vegetación húmeda, bajo riesgo\n• -0.1 a 0.1: Estrés hídrico leve\n• <-0.1: Estrés hídrico severo, alto riesgo',
  ndvi:    'NDVI — Índice de vegetación. Rango -1 a 1:\n• >0.5: Vegetación densa y sana\n• 0.2–0.5: Vegetación moderada\n• 0–0.2: Vegetación escasa o suelo desnudo\n• <0: Superficie no vegetada (agua, suelo expuesto)',
  firms:   'Focos activos detectados por satélite NASA (FIRMS) en las últimas 24 h. A mayor número de focos de alta confianza, mayor riesgo inmediato.',
  loss:    'Tasa de pérdida de cobertura forestal reciente. 0 = sin pérdida detectada, 1 = pérdida total en el área.',
  reports: 'Reportes ciudadanos verificados de humo, focos o incendios activos en la comuna.'
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
            const info = COMPONENT_INFO[key];
            return (
              <div key={key} className="risk-score-component">
                <span className="risk-component-label">
                  {COMPONENT_LABELS[key] || key}
                  {info && <IndexInfo info={info} label={COMPONENT_LABELS[key] || key} />}
                </span>
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
  const [scoreOverrides, setScoreOverrides] = useState({});

  // Merge Copernicus-confirmed scores on top of the loaded comunalScores so
  // the map tooltip and next panel open reflect the updated score immediately,
  // without requiring a full layer reload. Resets when regionData changes.
  const effectiveComunalScores = useMemo(
    () => (comunalScores ? { ...comunalScores, ...scoreOverrides } : null),
    [comunalScores, scoreOverrides]
  );

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

  const handleScoreUpdated = useCallback((updatedScore) => {
    if (!updatedScore || !selectedComuna) return;
    setScoreOverrides((prev) => ({ ...prev, [selectedComuna.comunaId]: updatedScore }));
    setSelectedComuna((prev) => prev ? { ...prev, score: updatedScore } : null);
  }, [selectedComuna]);

  return (
    <article className="dashboard-card territory-map-card">
      <div className="territory-header">
        <h3>Mapa territorial interactivo</h3>
        <button type="button" className="btn btn-secondary" onClick={onRetry} disabled={loading || refreshing}>
          {refreshing ? 'Actualizando...' : 'Actualizar capas'}
        </button>
      </div>

      {/* Persistent region-level risk breakdown panel: always visible when
          regionData is available, independent of the RISK_SCORE map-layer
          toggle (spec: risk-score-breakdown-panel). */}
      {regionData && <RiskScoreBadge regionData={regionData} />}

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
          {/* RISK_SCORE no es un toggle: el choropleth comunal y el panel de
              riesgo se muestran siempre (spec: risk-score-breakdown-panel). */}
          {['FIRMS', 'NDVI', 'NDMI', 'LOSS', 'ALERTS', 'REPORTS'].map((indicator) => (
            <label key={indicator} className="territory-toggle">
              <input
                type="checkbox"
                checked={visibleIndicators.includes(indicator)}
                onChange={() => toggleIndicator(indicator)}
              />
              <span>{indicator}</span>
            </label>
          ))}
          {/* Opt-in climate layers (DEC-B): off by default, each with its own
              color scale (DEC-A) rendered as a comuna choropleth. */}
          {CLIMATE_INDICATORS.map((indicator) => (
            <label key={indicator} className="territory-toggle">
              <input
                type="checkbox"
                checked={visibleIndicators.includes(indicator)}
                onChange={() => toggleIndicator(indicator)}
              />
              <span>{CLIMATE_SCALES[indicator]?.label || indicator}</span>
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
                comunalScores={effectiveComunalScores}
                onComunaHover={handleComunaHover}
                onComunaHoverEnd={handleComunaHoverEnd}
                onComunaClick={handleComunaClick}
              />
            )}

            {comunalGeoJson && CLIMATE_INDICATORS.filter((indicator) => visibleIndicators.includes(indicator)).map((indicator) => (
              <ClimateChoropleth
                key={`${regionData.regionId}-${indicator}`}
                geoJson={comunalGeoJson}
                indicator={indicator}
                valueMap={regionData.layers?.[indicator]}
              />
            ))}

            {visibleIndicators.filter((i) => i !== 'RISK_SCORE' && i !== 'REPORTS' && !CLIMATE_INDICATORS.includes(i)).map((indicator) => (
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
              regionId={selectedRegionId}
              onClose={() => setSelectedComuna(null)}
              onScoreUpdated={handleScoreUpdated}
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

              {CLIMATE_INDICATORS.filter((indicator) => visibleIndicators.includes(indicator)).map((indicator) => {
                const scale = CLIMATE_SCALES[indicator];
                if (!scale) return null;
                return (
                  <div key={indicator} className="territory-climate-legend">
                    <h5>{scale.label} ({scale.unit})</h5>
                    <ul>
                      {scale.bins.map((bin) => (
                        <li key={bin.label}>
                          <span className="territory-color-dot" style={{ backgroundColor: bin.color }} />
                          <span>{bin.label}</span>
                        </li>
                      ))}
                      <li>
                        <span className="territory-color-dot" style={{ backgroundColor: NEUTRAL_FILL }} />
                        <span>Sin datos</span>
                      </li>
                    </ul>
                  </div>
                );
              })}

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
