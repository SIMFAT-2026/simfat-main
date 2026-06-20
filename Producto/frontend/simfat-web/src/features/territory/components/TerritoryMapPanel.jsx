import { useEffect, useRef, useState, useCallback, useMemo, memo } from 'react';
import { createPortal } from 'react-dom';
import L from 'leaflet';
import ComunaRiskPanel from './ComunaRiskPanel';
import { GeoJSON, MapContainer, Marker, Pane, TileLayer, Tooltip, useMap } from 'react-leaflet';
import marker2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';
import EmptyState from '../../../components/EmptyState';
import ErrorMessage from '../../../components/ErrorMessage';
import LoadingSpinner from '../../../components/LoadingSpinner';
import WeatherSyncButton from '../../dashboard/components/WeatherSyncButton';
import { ALERT_LEVEL_CONFIG, CLIMATE_SCALES, RISK_SCORE_LEGEND, VEGETATION_SCALES } from '../constants/colorScales';

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
  NDVI: '#88419d',
  NDMI: '#0ea5e9',
  ALERTS: '#dc2626',
  FIRMS: '#ff4500',
  REPORTS: '#7c3aed',
  RISK_SCORE: '#0570b0',
  WIND: '#0891b2',
  HUMIDITY: '#2b8cbe',
  AIR_TEMP: '#dc2626',
  SOIL_TEMP: '#b45309'
};

const CLIMATE_INDICATORS = ['WIND', 'HUMIDITY', 'AIR_TEMP', 'SOIL_TEMP'];
const INDEX_CHOROPLETH_INDICATORS = ['NDVI', 'NDMI'];
const NEUTRAL_FILL = '#cbd5e1';
const POINT_LAYER_RENDERER = L.svg({ pane: 'territory-points-pane' });
const REPORT_LAYER_RENDERER = L.svg({ pane: 'territory-report-pane' });

const FIRMS_RECENCY_STYLES = {
  today: {
    label: 'Detecciones FIRMS de hoy',
    fillColor: '#ef4444',
    color: '#7f1d1d',
    opacity: 0.95,
    fillOpacity: 0.9,
    weight: 1.4,
    radiusOffset: 1
  },
  recent: {
    label: 'Detecciones FIRMS recientes',
    fillColor: '#f97316',
    color: '#9a3412',
    opacity: 0.75,
    fillOpacity: 0.45,
    weight: 1,
    radiusOffset: 0,
    dashArray: '3 3'
  },
  unknown: {
    label: 'Detecciones FIRMS sin fecha',
    fillColor: '#f59e0b',
    color: '#92400e',
    opacity: 0.65,
    fillOpacity: 0.35,
    weight: 1,
    radiusOffset: 0
  }
};

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



function comunaBaseStyle(score) {
  const level = score?.alertLevel || 'NORMAL';
  const cfg = ALERT_LEVEL_CONFIG[level] || ALERT_LEVEL_CONFIG.NORMAL;
  return {
    fillColor: cfg.fill,
    fillOpacity: score ? 0.34 : 0.10,
    color: cfg.color,
    weight: score ? cfg.weight : 0.8,
    dashArray: score ? cfg.dashArray : null,
    opacity: score ? 0.9 : 0.7
  };
}

const ComunaChoropleth = memo(function ComunaChoropleth({ geoJson, comunalScores, onComunaHover, onComunaHoverEnd, onComunaClick }) {
  if (!geoJson || !geoJson.features) return null;

  return (
    <GeoJSON
      key="choropleth"
      pane="comuna-risk-pane"
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
      pane="climate-layer-pane"
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

const VegetationChoropleth = memo(function VegetationChoropleth({ geoJson, indicator, comunalScores }) {
  if (!geoJson || !geoJson.features) return null;

  const scale = VEGETATION_SCALES[indicator];
  if (!scale) return null;

  return (
    <GeoJSON
      key={`vegetation-${indicator}`}
      pane="climate-layer-pane"
      data={geoJson}
      style={(feature) => {
        const comunaId = feature?.properties?.comunaId;
        const value = comunalScores?.[comunaId]?.[scale.valueKey];
        return {
          fillColor: vegetationColorForValue(indicator, value),
          fillOpacity: value != null ? 0.55 : 0.12,
          color: '#334155',
          weight: 0.8,
          opacity: 0.7
        };
      }}
      onEachFeature={(feature, layer) => {
        const comunaId = feature?.properties?.comunaId;
        const nombre = feature?.properties?.nombre || comunaId;
        const value = comunalScores?.[comunaId]?.[scale.valueKey];
        const valueLabel = value !== undefined && value !== null ? Number(value).toFixed(3) : 'Sin datos';
        layer.bindTooltip(`${nombre}: ${scale.label} ${valueLabel}`, { sticky: true });
      }}
    />
  );
});

// Same UTC-forcing rule as parseUtcDate, but returns a Date instead of a
// formatted string — needed to compare/sort hourly wind timestamps.
function toUtcDate(str) {
  if (!str) return null;
  const utc = /Z|[+-]\d{2}:?\d{2}$/.test(str) ? str : str + 'Z';
  return new Date(utc);
}

function santiagoDateKey(date) {
  if (!date || Number.isNaN(date.getTime())) return null;
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Santiago',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(date);
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

function firmsRecencyBucket(feature) {
  const detectedAt = toUtcDate(feature?.properties?.acquiredAt);
  if (!detectedAt || Number.isNaN(detectedAt.getTime())) return 'unknown';

  return santiagoDateKey(detectedAt) === santiagoDateKey(new Date()) ? 'today' : 'recent';
}

// past_hours=24 + forecast_days=1 in the Open-Meteo request can overlap into
// up to ~48h depending on what time the sync ran, so the slider isn't always
// a single day — show the date too once the series crosses that boundary.
function formatHourLabel(str, includeDate) {
  const date = toUtcDate(str);
  if (!date) return '—';
  return date.toLocaleString('es-CL', {
    timeZone: 'America/Santiago',
    ...(includeDate ? { day: '2-digit', month: '2-digit' } : {}),
    hour: '2-digit',
    minute: '2-digit'
  });
}

const COMPASS_LABELS = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];

// Open-Meteo's winddirection is the bearing the wind comes FROM (meteorological
// convention). For an arrow that visually shows where the wind is heading, we
// rotate by +180°; the compass label below still describes the source bearing.
function compassLabelFrom(directionFromDeg) {
  const index = Math.round(directionFromDeg / 45) % 8;
  return COMPASS_LABELS[index];
}

function featureCentroid(feature) {
  try {
    return L.geoJSON(feature).getBounds().getCenter();
  } catch {
    return null;
  }
}

function windArrowIcon(speed, bearingTo) {
  const color = climateColorForValue('WIND', speed);
  const html = `<div class="wind-arrow" style="transform: rotate(${bearingTo}deg); border-bottom-color: ${color};"></div>`;
  return L.divIcon({
    className: 'wind-arrow-icon',
    html,
    iconSize: [20, 20],
    iconAnchor: [10, 10]
  });
}

// Renders one rotated arrow per comuna centroid showing wind direction
// (spec: wind-arrow-overlay). Speed/direction come either from the latest
// daily observation or, when a slider hour is selected, from that comuna's
// hourly series — both populated by the same Open-Meteo sync.
const WindArrowLayer = memo(function WindArrowLayer({ geoJson, valueMap, hourIndex }) {
  if (!geoJson || !geoJson.features) return null;
  const values = valueMap?.values || {};

  const markers = geoJson.features.map((feature) => {
    const comunaId = feature?.properties?.comunaId;
    const entry = values[comunaId];
    if (!entry) return null;

    const hourlyPoint = hourIndex != null ? entry.hourly?.[hourIndex] : null;
    const speed = hourlyPoint ? hourlyPoint.speed : entry.value;
    const directionFrom = hourlyPoint ? hourlyPoint.direction : entry.direction;
    if (speed == null || directionFrom == null) return null;

    const center = featureCentroid(feature);
    if (!center) return null;

    const bearingTo = (directionFrom + 180) % 360;
    const nombre = feature?.properties?.nombre || comunaId;

    return (
      <Marker key={comunaId} position={center} pane="wind-arrow-pane" icon={windArrowIcon(speed, bearingTo)}>
        <Tooltip direction="top" offset={[0, -8]}>
          {nombre}: {Number(speed).toFixed(1)} km/h desde el {compassLabelFrom(directionFrom)}
        </Tooltip>
      </Marker>
    );
  }).filter(Boolean);

  return markers;
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
  if (props.indicator === 'FIRMS') return 'Detección satelital VIIRS';
  return props.label || props.name || props.indicator || 'Punto territorial';
}

function featureMeta(feature) {
  const props = feature?.properties || {};

  if (props.indicator === 'FIRMS') {
    const frp = props.frp != null ? `FRP: ${Number(props.frp).toFixed(1)} MW` : '';
    const conf = props.confidence === 'h' ? 'Alta confianza' : props.confidence === 'n' ? 'Confianza nominal' : '';
    const recency = FIRMS_RECENCY_STYLES[firmsRecencyBucket(feature)]?.label;
    const time = props.acquiredAt ? `Detectado: ${parseUtcDate(props.acquiredAt)}` : '';
    return [frp, conf, recency, time].filter(Boolean).join(' | ');
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

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function firmsConfidenceLabel(confidence) {
  if (confidence === 'h') return 'Alta confianza';
  if (confidence === 'n') return 'Confianza nominal';
  return confidence || 'Sin dato';
}

function firmsConfidenceHelp(confidence) {
  if (confidence === 'h') {
    return 'Alta: NASA estima mayor certeza de que la anomalía térmica corresponde a fuego.';
  }
  if (confidence === 'n') {
    return 'Nominal: detección válida de confianza media; conviene contrastarla con contexto local.';
  }
  return 'Confianza reportada por NASA FIRMS para la detección satelital.';
}

function featurePopupContent(feature) {
  const props = feature?.properties || {};
  const label = featureLabel(feature);
  const meta = featureMeta(feature);

  if (props.indicator !== 'FIRMS') {
    return `${escapeHtml(label)}${meta ? ` | ${escapeHtml(meta)}` : ''}`;
  }

  const frp = props.frp != null ? Number(props.frp).toFixed(1) : 'Sin dato';
  const confidence = firmsConfidenceLabel(props.confidence);
  const recency = FIRMS_RECENCY_STYLES[firmsRecencyBucket(feature)]?.label || 'Detección FIRMS';
  const detectedAt = props.acquiredAt ? parseUtcDate(props.acquiredAt) : 'Sin dato';

  return `
    <div class="firms-popup">
      <strong>${escapeHtml(label)}</strong>
      <div>FRP: ${escapeHtml(frp)} MW | ${escapeHtml(confidence)}</div>
      <div>${escapeHtml(recency)}</div>
      <div>Detectado: ${escapeHtml(detectedAt)}</div>
      <div class="firms-popup-help">
        <div><strong>FRP</strong>: potencia radiativa del fuego; valores más altos indican mayor intensidad térmica.</div>
        <div><strong>Confianza</strong>: ${escapeHtml(firmsConfidenceHelp(props.confidence))}</div>
      </div>
    </div>
  `;
}

function toPointStyle(indicator, feature) {
  const frp = feature?.properties?.frp;
  let radius = indicator === 'ALERTS' ? 8 : 7;

  if (indicator === 'FIRMS') {
    const recency = firmsRecencyBucket(feature);
    const recencyStyle = FIRMS_RECENCY_STYLES[recency] || FIRMS_RECENCY_STYLES.recent;
    radius = frp ? Math.min(4 + Math.sqrt(Number(frp)) / 4, 8) : 5;
    return {
      pane: 'territory-points-pane',
      renderer: POINT_LAYER_RENDERER,
      radius: radius + recencyStyle.radiusOffset,
      fillColor: recencyStyle.fillColor,
      color: recencyStyle.color,
      weight: recencyStyle.weight,
      opacity: recencyStyle.opacity,
      fillOpacity: recencyStyle.fillOpacity,
      dashArray: recencyStyle.dashArray
    };
  }

  return {
    pane: indicator === 'REPORTS' ? 'territory-report-pane' : 'territory-points-pane',
    renderer: indicator === 'REPORTS' ? REPORT_LAYER_RENDERER : POINT_LAYER_RENDERER,
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
  firms:   'Detecciones FIRMS',
  ndvi:    'Índice vegetación (NDVI)',
  reports: 'Reportes'
};

const INDICATOR_LABELS = {
  RISK_SCORE: 'Riesgo',
  FIRMS: 'FIRMS',
  NDVI: 'Índice veg.',
  NDMI: 'Humedad veg.',
  ALERTS: 'Alertas',
  REPORTS: 'Reportes',
  WIND: 'Viento',
  HUMIDITY: 'Humedad rel.',
  AIR_TEMP: 'Temp. del aire',
  SOIL_TEMP: 'Temp. del suelo'
};

const INDICATOR_FULL_LABELS = {
  RISK_SCORE: 'Riesgo comunal',
  FIRMS: 'Detecciones FIRMS',
  NDVI: 'Índice vegetación (NDVI)',
  NDMI: 'Humedad vegetación (NDMI)',
  ALERTS: 'Alertas',
  REPORTS: 'Reportes',
  WIND: 'Viento',
  HUMIDITY: 'Humedad relativa',
  AIR_TEMP: 'Temperatura del aire',
  SOIL_TEMP: 'Temperatura del suelo'
};

function IndexInfo({ info, label }) {
  const [tooltipStyle, setTooltipStyle] = useState(null);
  const btnRef = useRef(null);
  const tooltipRef = useRef(null);

  useEffect(() => {
    if (!tooltipStyle) return undefined;

    function handleDocumentPointerDown(event) {
      if (btnRef.current?.contains(event.target) || tooltipRef.current?.contains(event.target)) {
        return;
      }
      setTooltipStyle(null);
    }

    document.addEventListener('pointerdown', handleDocumentPointerDown);
    return () => document.removeEventListener('pointerdown', handleDocumentPointerDown);
  }, [tooltipStyle]);

  function handleToggle(e) {
    e.stopPropagation();
    if (tooltipStyle) {
      setTooltipStyle(null);
      return;
    }
    const rect = btnRef.current.getBoundingClientRect();
    const TOOLTIP_W = 238;
    const fitsRight = rect.right + 8 + TOOLTIP_W < window.innerWidth;
    setTooltipStyle({
      position: 'fixed',
      top: `${rect.top + rect.height / 2}px`,
      left: fitsRight ? `${rect.right + 8}px` : `${rect.left - TOOLTIP_W - 8}px`,
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
        <span ref={tooltipRef} className="comp-info-tooltip" style={tooltipStyle}>
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
  fwi: {
    description: 'Índice de Peligro de Incendio meteorológico.',
    rawLabel: 'FWI observado',
    rawValue: (component) => component?.rawValue,
    scale: 'Escala raw 0–100+:\n• 0–11: Bajo\n• 11–21: Moderado\n• 21–38: Alto\n• 38–50: Muy alto\n• >50: Extremo'
  },
  ndmi: {
    description: 'Humedad de la vegetación derivada de NDMI.',
    rawLabel: 'NDMI observado',
    rawValue: (component) => component?.rawValue,
    scale: 'Escala raw -1 a 1:\n• >0.1: Vegetación húmeda, bajo riesgo\n• -0.1 a 0.1: Estrés hídrico leve\n• <-0.1: Estrés hídrico severo'
  },
  firms: {
    description: 'Detecciones satelitales recientes de anomalías térmicas NASA FIRMS.',
    rawLabel: 'Detecciones FIRMS',
    rawValue: (component) => component?.focosCount,
    scale: 'A mayor cantidad de focos y FRP, mayor riesgo inmediato.'
  },
  ndvi: {
    description: 'Índice de vegetación derivado de NDVI.',
    rawLabel: 'NDVI observado',
    rawValue: (component) => component?.rawValue,
    scale: 'Escala raw -1 a 1:\n• >0.5: Vegetación densa y sana\n• 0.2–0.5: Vegetación moderada\n• 0–0.2: Vegetación escasa\n• <0: Superficie no vegetada'
  },
  reports: {
    description: 'Reportes ciudadanos verificados.',
    rawLabel: 'Reportes activos',
    rawValue: (component) => component?.count,
    scale: 'A mayor cantidad de reportes verificados, mayor aporte al riesgo.'
  }
};

const VISIBLE_RISK_COMPONENTS = new Set(['fwi', 'ndmi', 'firms', 'ndvi', 'reports']);
const POINT_LAYER_INDICATORS = ['FIRMS', 'ALERTS', 'REPORTS'];
const MAP_TOGGLE_INDICATORS = ['RISK_SCORE', 'FIRMS', 'NDVI', 'NDMI', 'ALERTS', 'REPORTS'];
const ALERT_TOOLTIP_COMPONENTS = ['firms', 'fwi', 'ndmi', 'ndvi'];

function componentScoreParts(component) {
  const score = typeof component?.score === 'number' ? component.score : null;
  const max = typeof component?.weight === 'number' ? component.weight : 1;

  return {
    value: score != null ? (score * 100).toFixed(0) : '—',
    max: (max * 100).toFixed(0),
    fillPct: score != null && max > 0 ? Math.min((score / max) * 100, 100) : 0
  };
}

function formatRawValue(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return 'Sin dato';
  }

  return Number.isInteger(Number(value)) ? String(Number(value)) : Number(value).toFixed(2);
}

function componentInfoText(key, component, scoreParts) {
  const info = COMPONENT_INFO[key];
  if (!info) return null;
  const rawValue = info.rawValue?.(component);

  return [
    info.description,
    `Aporte mostrado: ${scoreParts.value}/${scoreParts.max} pts del score global.`,
    `${info.rawLabel}: ${formatRawValue(rawValue)}.`,
    info.scale
  ].filter(Boolean).join('\n');
}

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
          {Object.entries(components).filter(([key]) => VISIBLE_RISK_COMPONENTS.has(key)).map(([key, comp]) => {
            const scoreParts = componentScoreParts(comp);
            const info = componentInfoText(key, comp, scoreParts);
            return (
              <div key={key} className="risk-score-component">
                <span className="risk-component-label">{COMPONENT_LABELS[key] || key}</span>
                {info ? <IndexInfo info={info} label={COMPONENT_LABELS[key] || key} /> : <span />}
                <div className="risk-component-bar-wrap">
                  <div
                    className="risk-component-bar"
                    style={{ width: `${scoreParts.fillPct}%`, backgroundColor: config.color }}
                  />
                </div>
                <span className="risk-component-value">{scoreParts.value}<small>/{scoreParts.max}</small></span>
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
  const photoUrl = props.photoUrl || (Array.isArray(props.photoUrls) && props.photoUrls[0]) || null;
  return (
    <div className="report-card" style={{ left: pos.x + 14, top: pos.y - 10 }}>
      <div className="report-card-header">
        <span className="report-card-category" style={{ color }}>{REPORT_CATEGORY_LABELS[category] || category}</span>
        <button type="button" className="panel-close" onClick={onClose} aria-label="Cerrar">×</button>
      </div>
      {photoUrl && (
        <img
          src={photoUrl}
          alt="Foto del reporte"
          className="report-card-photo"
          style={{ width: '100%', maxHeight: '140px', objectFit: 'cover', borderRadius: '4px', marginBottom: '6px' }}
        />
      )}
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

function firmsRecencyCount(regionData, bucket) {
  return (regionData?.layers?.FIRMS?.features || [])
    .filter((feature) => firmsRecencyBucket(feature) === bucket)
    .length;
}

function tooltipComponentEntries(components) {
  const allEntries = Object.entries(components || {})
    .filter(([key, v]) => VISIBLE_RISK_COMPONENTS.has(key) && v != null);
  const entries = allEntries.filter(([, v]) => v > 0);

  if (!allEntries.length) {
    return [];
  }

  const hasActiveFirms = Number(components?.firms || 0) > 0;
  if (!hasActiveFirms) {
    return entries.sort(([, a], [, b]) => b - a).slice(0, 2);
  }

  const byKey = new Map(allEntries);
  const prioritized = ALERT_TOOLTIP_COMPONENTS
    .filter((key) => byKey.has(key))
    .map((key) => [key, byKey.get(key)]);
  const fallback = entries
    .filter(([key]) => !ALERT_TOOLTIP_COMPONENTS.includes(key))
    .sort(([, a], [, b]) => b - a);

  return [...prioritized, ...fallback].slice(0, 4);
}

function vegetationColorForValue(indicator, value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return NEUTRAL_FILL;
  }
  const scale = VEGETATION_SCALES[indicator];
  if (!scale) return NEUTRAL_FILL;
  const numeric = Number(value);
  const bin = scale.bins.find((b) => numeric < b.max);
  return bin ? bin.color : scale.bins[scale.bins.length - 1].color;
}

// WMO weather_code (Open-Meteo) -> categoria visual. El codigo ya distingue
// despejado/nublado/lluvia/nieve por si solo (no requiere combinarlo con
// cloud_cover, que ademas no existe en el bloque "daily" de Open-Meteo).
function weatherCategoryFromCode(code) {
  if (code === null || code === undefined) return null;
  if (code === 0 || code === 1) return 'sunny';
  if ([2, 3, 45, 48].includes(code)) return 'cloudy';
  if ([71, 73, 75, 77, 85, 86].includes(code)) return 'snowy';
  if ([51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99].includes(code)) return 'rainy';
  return 'cloudy';
}

// Ilustraciones planas (varios elementos, no un glifo de icono unico) para el
// widget de clima del tooltip comunal. Sin animacion por ahora (a futuro).
function WeatherIllustration({ category, size = 30 }) {
  if (category === 'sunny') {
    return (
      <svg width={size} height={size} viewBox="0 0 32 32" aria-label="Despejado">
        <circle cx="16" cy="16" r="7" fill="#facc15" />
        {[0, 45, 90, 135, 180, 225, 270, 315].map((deg) => (
          <line
            key={deg}
            x1="16" y1="3" x2="16" y2="7"
            stroke="#f59e0b" strokeWidth="2" strokeLinecap="round"
            transform={`rotate(${deg} 16 16)`}
          />
        ))}
      </svg>
    );
  }
  if (category === 'rainy') {
    return (
      <svg width={size} height={size} viewBox="0 0 32 32" aria-label="Lluvia">
        <path d="M9 18a5 5 0 0 1-1-9.9 6.5 6.5 0 0 1 12.6-2A5.5 5.5 0 0 1 23 18Z" fill="#94a3b8" />
        <line x1="11" y1="21" x2="9" y2="27" stroke="#38bdf8" strokeWidth="2" strokeLinecap="round" />
        <line x1="16" y1="21" x2="14" y2="27" stroke="#38bdf8" strokeWidth="2" strokeLinecap="round" />
        <line x1="21" y1="21" x2="19" y2="27" stroke="#38bdf8" strokeWidth="2" strokeLinecap="round" />
      </svg>
    );
  }
  if (category === 'snowy') {
    return (
      <svg width={size} height={size} viewBox="0 0 32 32" aria-label="Nieve">
        <path d="M9 18a5 5 0 0 1-1-9.9 6.5 6.5 0 0 1 12.6-2A5.5 5.5 0 0 1 23 18Z" fill="#cbd5e1" />
        <circle cx="11" cy="25" r="1.4" fill="#bae6fd" />
        <circle cx="16" cy="27" r="1.4" fill="#bae6fd" />
        <circle cx="21" cy="25" r="1.4" fill="#bae6fd" />
      </svg>
    );
  }
  // cloudy (default)
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-label="Nublado">
      <path d="M9 21a5.5 5.5 0 0 1-1-10.9A7 7 0 0 1 21.5 8 6 6 0 0 1 22 21Z" fill="#cbd5e1" />
      <circle cx="13" cy="11" r="4" fill="#facc15" opacity="0.7" />
    </svg>
  );
}

function ComunaTooltip({ comunaId, nombre, score, pos }) {
  if (!comunaId || !pos) return null;
  const level = ALERT_LEVEL_CONFIG[score?.alertLevel] || ALERT_LEVEL_CONFIG.NORMAL;
  const pct = typeof score?.scoreComposite === 'number' ? (score.scoreComposite * 100).toFixed(0) : null;
  const displayNombre = nombre || score?.nombreComuna || comunaId;
  const mode = score?.mode;

  const componentEntries = tooltipComponentEntries(score?.components);
  const fwiInputs = score?.fwiInputs;
  const weatherCategory = weatherCategoryFromCode(fwiInputs?.weatherCode);

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
      {fwiInputs && weatherCategory && (
        <div className="comuna-tooltip-weather">
          <WeatherIllustration category={weatherCategory} size={28} />
          <span className="comuna-tooltip-weather-text">
            {typeof fwiInputs.tempMin === 'number' && typeof fwiInputs.tempMax === 'number'
              ? `${fwiInputs.tempMin.toFixed(0)}°/${fwiInputs.tempMax.toFixed(0)}°`
              : typeof fwiInputs.tempMax === 'number' ? `${fwiInputs.tempMax.toFixed(0)}°` : '—'}
            {' · '}
            {typeof fwiInputs.humidityMin === 'number' ? `${fwiInputs.humidityMin.toFixed(0)}%` : '—'}
            {' · '}
            {typeof fwiInputs.windMax === 'number' ? `${fwiInputs.windMax.toFixed(0)}km/h` : '—'}
            {typeof fwiInputs.precip === 'number' && fwiInputs.precip > 0 ? ` · ${fwiInputs.precip.toFixed(1)}mm` : ''}
          </span>
        </div>
      )}
      {score ? (
        <>
          <div className="comuna-tooltip-level" style={{ color: level.darkUiColor }}>
            {level.label}
          </div>
          <div className="comuna-tooltip-score-row">
            <div className="comuna-tooltip-bar-wrap">
              <div
                className="comuna-tooltip-bar"
                style={{ width: `${pct}%`, backgroundColor: level.darkUiColor }}
              />
            </div>
            <span className="comuna-tooltip-pct" style={{ color: level.darkUiColor }}>{pct}<small>/100</small></span>
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
  const selectedRegionLabel = regionOptions.find((region) => region.id === selectedRegionId)?.label || selectedRegionId;

  const [hoveredComuna, setHoveredComuna] = useState(null);
  const [tooltipPos, setTooltipPos] = useState(null);
  const [selectedComuna, setSelectedComuna] = useState(null);
  const [selectedReport, setSelectedReport] = useState(null);
  const [reportPos, setReportPos] = useState(null);
  const [scoreOverrides, setScoreOverrides] = useState({});
  const [windHourIndex, setWindHourIndex] = useState(null);

  // Hourly timestamps shared across comunas (they sync close enough in time
  // to use one comuna's series as the slider's tick labels). Falls back to
  // empty when no comuna has hourly data yet (sync not run / still loading).
  const windHourlyTimestamps = useMemo(() => {
    const values = regionData?.layers?.WIND?.values || {};
    const withHourly = Object.values(values).find((v) => Array.isArray(v.hourly) && v.hourly.length > 0);
    return withHourly ? withHourly.hourly.map((point) => point.timestamp) : [];
  }, [regionData]);

  // Defaults the slider to the hour closest to "now" whenever the wind series
  // (re)loads, so users see the current wind state before scrubbing manually.
  useEffect(() => {
    if (!windHourlyTimestamps.length) {
      setWindHourIndex(null);
      return;
    }
    const now = Date.now();
    let closestIndex = 0;
    let closestDiff = Infinity;
    windHourlyTimestamps.forEach((ts, index) => {
      const diff = Math.abs((toUtcDate(ts)?.getTime() ?? Infinity) - now);
      if (diff < closestDiff) {
        closestDiff = diff;
        closestIndex = index;
      }
    });
    setWindHourIndex(closestIndex);
  }, [windHourlyTimestamps]);

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
      {/* Persistent region-level risk breakdown panel: always visible when
          regionData is available, independent of the RISK_SCORE map-layer
          toggle (spec: risk-score-breakdown-panel). */}
      {regionData && (
        <section className="regional-summary-panel" aria-label="Panel de resumen regional">
          <h4 className="regional-summary-title">Panel de resumen regional: Región {selectedRegionLabel}</h4>
          <RiskScoreBadge regionData={regionData} />
        </section>
      )}

      <div className="filter-bar territory-controls">
        <h3 className="territory-controls-title">Mapa territorial interactivo</h3>
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
          {MAP_TOGGLE_INDICATORS.map((indicator) => (
            <label key={indicator} className="territory-toggle" title={INDICATOR_FULL_LABELS[indicator] || indicator}>
              <input
                type="checkbox"
                aria-label={INDICATOR_FULL_LABELS[indicator] || indicator}
                checked={visibleIndicators.includes(indicator)}
                onChange={() => toggleIndicator(indicator)}
              />
              <span>{INDICATOR_LABELS[indicator] || indicator}</span>
            </label>
          ))}
          {/* Opt-in climate layers (DEC-B): off by default, each with its own
              color scale (DEC-A) rendered as a comuna choropleth. */}
          {CLIMATE_INDICATORS.map((indicator) => (
            <label key={indicator} className="territory-toggle" title={INDICATOR_FULL_LABELS[indicator] || indicator}>
              <input
                type="checkbox"
                aria-label={INDICATOR_FULL_LABELS[indicator] || indicator}
                checked={visibleIndicators.includes(indicator)}
                onChange={() => toggleIndicator(indicator)}
              />
              <span>{INDICATOR_LABELS[indicator] || CLIMATE_SCALES[indicator]?.label || indicator}</span>
            </label>
          ))}
        </div>

        <div className="territory-control-actions">
          <button
            type="button"
            className="btn btn-secondary territory-refresh-button"
            onClick={onRetry}
            disabled={loading || refreshing}
          >
            {refreshing ? 'Actualizando...' : 'Actualizar capas'}
          </button>
          <WeatherSyncButton regionId={selectedRegionId} />
          <small style={{ color: '#94a3b8', fontSize: '0.68rem', marginTop: 2 }}>
            ⓘ La sincronización puede tardar aprox. 2–3 min
          </small>
        </div>

        {visibleIndicators.includes('WIND') && windHourlyTimestamps.length > 0 && (
          <div className="wind-hour-slider">
            <label htmlFor="wind-hour-range">
              Dirección del viento — {windHourIndex != null
                ? formatHourLabel(windHourlyTimestamps[windHourIndex], windHourlyTimestamps.length > 24)
                : '—'}
            </label>
            <input
              id="wind-hour-range"
              type="range"
              min={0}
              max={windHourlyTimestamps.length - 1}
              value={windHourIndex ?? 0}
              onChange={(event) => setWindHourIndex(Number(event.target.value))}
            />
          </div>
        )}
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

            <Pane name="comuna-risk-pane" style={{ zIndex: 410 }}>
              {comunalGeoJson && visibleIndicators.includes('RISK_SCORE') && (
                <ComunaChoropleth
                  geoJson={comunalGeoJson}
                  comunalScores={effectiveComunalScores}
                  onComunaHover={handleComunaHover}
                  onComunaHoverEnd={handleComunaHoverEnd}
                  onComunaClick={handleComunaClick}
                />
              )}
            </Pane>

            <Pane name="climate-layer-pane" style={{ zIndex: 420 }}>
              {comunalGeoJson && INDEX_CHOROPLETH_INDICATORS.filter((indicator) => visibleIndicators.includes(indicator)).map((indicator) => (
                <VegetationChoropleth
                  key={`${regionData.regionId}-${indicator}`}
                  geoJson={comunalGeoJson}
                  indicator={indicator}
                  comunalScores={effectiveComunalScores}
                />
              ))}
              {comunalGeoJson && CLIMATE_INDICATORS.filter((indicator) => visibleIndicators.includes(indicator)).map((indicator) => (
                <ClimateChoropleth
                  key={`${regionData.regionId}-${indicator}`}
                  geoJson={comunalGeoJson}
                  indicator={indicator}
                  valueMap={regionData.layers?.[indicator]}
                />
              ))}
            </Pane>

            <Pane name="wind-arrow-pane" style={{ zIndex: 430 }}>
              {comunalGeoJson && visibleIndicators.includes('WIND') && (
                <WindArrowLayer
                  geoJson={comunalGeoJson}
                  valueMap={regionData.layers?.WIND}
                  hourIndex={windHourIndex}
                />
              )}
            </Pane>

            <Pane name="territory-points-pane" style={{ zIndex: 650 }}>
              {visibleIndicators.filter((i) => POINT_LAYER_INDICATORS.includes(i) && i !== 'REPORTS').map((indicator) => (
                <GeoJSON
                  key={`${regionData.regionId}-${indicator}`}
                  pane="territory-points-pane"
                  data={regionData.layers[indicator]}
                  pointToLayer={(feature, latlng) => L.circleMarker(latlng, toPointStyle(indicator, feature))}
                  onEachFeature={(feature, layer) => {
                    layer.bindPopup(featurePopupContent(feature));
                    layer.on('add mouseover', () => layer.bringToFront());
                  }}
                />
              ))}
            </Pane>

            <Pane name="territory-report-pane" style={{ zIndex: 660 }}>
              {visibleIndicators.includes('REPORTS') && regionData.layers.REPORTS && (
                <GeoJSON
                  key={`${regionData.regionId}-REPORTS`}
                  pane="territory-report-pane"
                  data={regionData.layers.REPORTS}
                  pointToLayer={(feature, latlng) => L.circleMarker(latlng, toPointStyle('REPORTS', feature))}
                  onEachFeature={(feature, layer) => {
                    layer.on('add mouseover', () => layer.bringToFront());
                    layer.on('click', (e) => {
                      setSelectedReport(feature);
                      setReportPos({ x: e.containerPoint.x, y: e.containerPoint.y });
                    });
                  }}
                />
              )}
            </Pane>
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
              key={selectedComuna.comunaId}
              comunaId={selectedComuna.comunaId}
              score={effectiveComunalScores?.[selectedComuna.comunaId] || selectedComuna.score}
              regionId={selectedRegionId}
              onClose={() => setSelectedComuna(null)}
              onScoreUpdated={handleScoreUpdated}
              canSync={false}
            />
          ) : (
            <div className="territory-legend">
              <h4>Leyenda de capas</h4>
              <div className="territory-legend-section">
                <h5>Puntos del mapa</h5>
                <ul>
                  {POINT_LAYER_INDICATORS.map((indicator) => (
                    indicator === 'FIRMS' ? (
                      Object.entries(FIRMS_RECENCY_STYLES)
                        .filter(([bucket]) => bucket !== 'unknown' || firmsRecencyCount(regionData, bucket) > 0)
                        .map(([bucket, style]) => (
                          <li key={`FIRMS-${bucket}`}>
                            <span
                              className="territory-color-dot"
                              style={{
                                backgroundColor: style.fillColor,
                                opacity: style.fillOpacity,
                                border: style.dashArray ? `1px dashed ${style.color}` : 'none'
                              }}
                            />
                            <span>{style.label}</span>
                            <strong>{firmsRecencyCount(regionData, bucket)}</strong>
                          </li>
                        ))
                    ) : (
                      <li key={indicator}>
                        <span
                          className="territory-color-dot"
                          style={{ backgroundColor: INDICATOR_COLORS[indicator] || '#64748b' }}
                        />
                        <span>{INDICATOR_FULL_LABELS[indicator] || indicator}</span>
                        <strong>{indicatorCount(regionData, indicator)}</strong>
                      </li>
                    )
                  ))}
                </ul>
              </div>

              {visibleIndicators.includes('RISK_SCORE') && (
                <div className="territory-legend-section">
                  <h5>Riesgo comunal</h5>
                  <ul>
                    {RISK_SCORE_LEGEND.map((item) => (
                      <li key={item.label}>
                        <span
                          className="territory-color-dot territory-risk-swatch"
                          style={{ backgroundColor: item.color, borderColor: item.borderColor, color: item.borderColor }}
                          aria-hidden="true"
                        />
                        <span>{item.label}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {INDEX_CHOROPLETH_INDICATORS.filter((indicator) => visibleIndicators.includes(indicator)).map((indicator) => {
                const scale = VEGETATION_SCALES[indicator];
                if (!scale) return null;
                return (
                  <div key={indicator} className="territory-legend-section">
                    <h5>{scale.label}</h5>
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

              {CLIMATE_INDICATORS.filter((indicator) => visibleIndicators.includes(indicator)).map((indicator) => {
                const scale = CLIMATE_SCALES[indicator];
                if (!scale) return null;
                return (
                  <div key={indicator} className="territory-legend-section">
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
