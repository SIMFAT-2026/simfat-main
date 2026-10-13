import { useEffect, useRef, useState, useCallback, useMemo, memo } from 'react';
import L from 'leaflet';
import ComunaRiskPanel from './ComunaRiskPanel';
import ComunaMapbiomasSection from './ComunaMapbiomasSection';
import { MAPBIOMAS_FIRE_CITATION } from '../utils/mapbiomasCitations';
import { comunaPanelOrigin, panelYear } from '../utils/comunaMapbiomas';
import { GeoJSON, MapContainer, Marker, Pane, TileLayer, Tooltip, useMap } from 'react-leaflet';
import marker2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';
import EmptyState from '../../../components/EmptyState';
import ErrorMessage from '../../../components/ErrorMessage';
import LoadingSpinner from '../../../components/LoadingSpinner';
import WeatherSyncButton from '../../dashboard/components/WeatherSyncButton';
import {
  ALERT_LEVEL_CONFIG,
  BURNED_SCALE,
  CLIMATE_SCALES,
  NEUTRAL_FILL,
  VEGETATION_SCALES,
  burnedColorFor
} from '../constants/colorScales';
import { burnedTooltipLines, landcoverLineFor, landcoverToggleLabel } from '../utils/landcoverLayer';
import { burnedLegendNote, burnedLineFor, burnedToggleLabel, mapbiomasOwner } from '../utils/mapbiomasLayer';
import { CLIMATE_INDICATORS } from '../constants/indicators';
import { burnedFillOpacity } from '../utils/mapbiomasStyle';
import LayerToggleGroups from './LayerToggleGroups';
import RegionalSummary from './RegionalSummary';
import { regionalSummary } from '../utils/regionalSummary';
import { alertTrigger } from '../utils/alertTrigger';
import { compassLabelFrom } from '../utils/compass';
import { REPORT_CARD_COLORS, isLongDescription } from '../utils/reportCard';
import { comunaRiskStyle, riskLegendItems } from '../utils/riskPalette';
import {
  WIND_ARROW,
  WIND_ARROW_HALO_WIDTH,
  WIND_ARROW_LEGEND,
  WIND_ARROW_PATH,
  windArrowHtml,
  windArrowSize
} from '../utils/windArrow';
import { modeLabel } from '../../../utils/labels';
import MapbiomasYearControl from './MapbiomasYearControl';
import { chipLabel, controlVisible } from '../utils/yearControl';
import { LandcoverChoropleth, LandcoverLegend } from './LandcoverLayer';

// LocalDateTime de Java llega sin 'Z' — JS lo interpreta como hora local del browser.
// Esta utilidad fuerza UTC antes de convertir a Santiago para evitar doble conversión.
function parseUtcDate(str, opts = {}) {
  if (!str) return '—';
  const utc = /Z|[+-]\d{2}:?\d{2}$/.test(str) ? str : str + 'Z';
  return new Date(utc).toLocaleString('es-CL', {
    timeZone: 'America/Santiago',
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', hour12: false,
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
  SOIL_TEMP: '#b45309',
  BURNED: '#AC4B28',
  LANDCOVER: '#7A5900'
};

const INDEX_CHOROPLETH_INDICATORS = ['NDVI', 'NDMI'];

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



const ComunaChoropleth = memo(function ComunaChoropleth({ geoJson, comunalScores, onComunaHover, onComunaHoverEnd, onComunaClick }) {
  // react-leaflet only rebuilds the underlying L.geoJSON layer when `data` (the
  // polygons) changes — onEachFeature's handlers are bound once at that point
  // and would otherwise close over a stale `comunalScores` forever (e.g. after
  // a Copernicus sync updates scores without touching the polygons). Reading
  // through a ref keeps the handlers seeing the latest scores on every hover.
  const scoresRef = useRef(comunalScores);
  scoresRef.current = comunalScores;

  if (!geoJson || !geoJson.features) return null;

  return (
    <GeoJSON
      key="choropleth"
      pane="comuna-risk-pane"
      data={geoJson}
      style={(feature) => {
        const score = scoresRef.current?.[feature?.properties?.comunaId];
        return comunaRiskStyle(score);
      }}
      onEachFeature={(feature, layer) => {
        const comunaId = feature?.properties?.comunaId;
        const nombre = feature?.properties?.nombre || comunaId;

        layer.on('mouseover', (e) => {
          const score = scoresRef.current?.[comunaId];
          layer.setStyle({ fillOpacity: 0.75, weight: 1.6, color: '#0f172a' });
          onComunaHover?.(comunaId, nombre, score, e.containerPoint);
        });

        layer.on('mouseout', () => {
          const score = scoresRef.current?.[comunaId];
          layer.setStyle(comunaRiskStyle(score));
          onComunaHoverEnd?.();
        });

        layer.on('click', () => {
          const score = scoresRef.current?.[comunaId];
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
  // Same stale-closure issue as ComunaChoropleth/VegetationChoropleth: onEachFeature
  // binds once when the polygons are first drawn, so bindTooltip's content would
  // otherwise be frozen at that weather value forever once regionData refreshes.
  const valueMapRef = useRef(valueMap);
  valueMapRef.current = valueMap;

  if (!geoJson || !geoJson.features) return null;

  const unit = valueMap?.unit || CLIMATE_SCALES[indicator]?.unit || '';

  return (
    <GeoJSON
      key={`climate-${indicator}`}
      pane="climate-layer-pane"
      data={geoJson}
      style={(feature) => {
        const comunaId = feature?.properties?.comunaId;
        const entry = valueMapRef.current?.values?.[comunaId];
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
        layer.bindTooltip('', { sticky: true });
        layer.on('mouseover', () => {
          const entry = valueMapRef.current?.values?.[comunaId];
          const entryUnit = valueMapRef.current?.unit || unit;
          const valueLabel = entry?.value !== undefined && entry?.value !== null
            ? `${Number(entry.value).toFixed(1)} ${entry.unit || entryUnit}`
            : 'Sin datos';
          layer.setTooltipContent(`${nombre}: ${valueLabel}`);
        });
      }}
    />
  );
});

// Renders the yearly MapBiomas BURNED layer as a comuna choropleth. Another
// year restyles the same polygons (style and tooltip read the latest layer
// through refs), and `stale` dims the previous year while the new one loads. While the layer is not loaded (loading or
// failed) every comuna is neutral and the tooltip says why.
// When `interactive` is false (RISK_SCORE is visible) the polygons take no
// pointer events, so hover and click fall through to the risk layer below, which
// shows the burned line in its own tooltip. When it owns hover (no RISK_SCORE)
// and LANDCOVER is visible and loaded, `landcoverLayer` adds the cover line to
// its tooltip. `emphasizeOutline` thickens the neutral outline while the layer
// is the only thing on the map and not loaded.
const BurnedChoropleth = memo(function BurnedChoropleth({
  geoJson,
  layer,
  loading,
  landcoverLayer = null,
  interactive = true,
  emphasizeOutline = false,
  stale = false,
  onComunaClick
}) {
  const layerRef = useRef(layer);
  layerRef.current = layer;
  const loadingRef = useRef(loading);
  loadingRef.current = loading;
  const landcoverRef = useRef(landcoverLayer);
  landcoverRef.current = landcoverLayer;

  if (!geoJson || !geoJson.features) return null;

  return (
    <GeoJSON
      key={`burned-${interactive ? 'i' : 'n'}-${emphasizeOutline ? 'e' : 'p'}`}
      pane="burned-layer-pane"
      interactive={interactive}
      data={geoJson}
      style={(feature) => {
        const comunaId = feature?.properties?.comunaId;
        const entry = layerRef.current?.values?.[comunaId];
        return {
          fillColor: burnedColorFor(entry),
          fillOpacity: burnedFillOpacity({ hasData: entry?.available === true, stale }),
          color: '#334155',
          weight: emphasizeOutline && !entry ? 1.6 : 0.8,
          opacity: emphasizeOutline && !entry ? 0.95 : 0.7
        };
      }}
      onEachFeature={(feature, layerGeo) => {
        if (!interactive) return;
        const comunaId = feature?.properties?.comunaId;
        const nombre = feature?.properties?.nombre || comunaId;
        layerGeo.bindTooltip('', { sticky: true });
        layerGeo.on('mouseover', () => {
          const current = layerRef.current;
          if (!current) {
            layerGeo.setTooltipContent(`${nombre}: ${loadingRef.current ? 'Cargando…' : 'Sin datos de Fuego'}`);
            return;
          }
          const cover = landcoverRef.current;
          const lines = burnedTooltipLines({
            nombre,
            year: current.year,
            entry: current.values?.[comunaId],
            landcoverLine: cover ? landcoverLineFor(cover.values?.[comunaId], cover.year) : ''
          });
          if (lines.length === 1) {
            layerGeo.setTooltipContent(lines[0]);
            return;
          }
          // Several rows: build a DOM node so names and labels are never read as markup.
          const root = document.createElement('div');
          lines.forEach((line) => {
            const row = document.createElement('div');
            row.textContent = line;
            root.appendChild(row);
          });
          layerGeo.setTooltipContent(root);
        });
        // Owning hover also owns click: opens the same comuna panel as RISK_SCORE.
        layerGeo.on('click', () => onComunaClick?.(comunaId, nombre));
      }}
    />
  );
});

const VegetationChoropleth = memo(function VegetationChoropleth({ geoJson, indicator, comunalScores }) {
  // Same stale-closure issue as ComunaChoropleth: onEachFeature binds once when
  // the polygons are first drawn, so bindTooltip's content would otherwise be
  // frozen at that NDVI/NDMI value forever (e.g. after a Copernicus sync).
  const scoresRef = useRef(comunalScores);
  scoresRef.current = comunalScores;

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
        const value = scoresRef.current?.[comunaId]?.[scale.valueKey];
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
        layer.bindTooltip('', { sticky: true });
        layer.on('mouseover', () => {
          const value = scoresRef.current?.[comunaId]?.[scale.valueKey];
          const valueLabel = value !== undefined && value !== null ? Number(value).toFixed(3) : 'Sin datos';
          layer.setTooltipContent(`${nombre}: ${scale.label} ${valueLabel}`);
        });
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

function featureCentroid(feature) {
  try {
    return L.geoJSON(feature).getBounds().getCenter();
  } catch {
    return null;
  }
}

function windArrowIcon(speed, bearingTo) {
  const size = windArrowSize(speed);
  return L.divIcon({
    className: 'wind-arrow-icon',
    html: windArrowHtml(speed, bearingTo),
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2]
  });
}

function WindArrowSample({ size }) {
  return (
    <svg className="territory-wind-legend-arrow" width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <path
        d={WIND_ARROW_PATH}
        fill={WIND_ARROW.fill}
        stroke={WIND_ARROW.halo}
        strokeWidth={WIND_ARROW_HALO_WIDTH}
        strokeLinejoin="round"
        paintOrder="stroke"
      />
    </svg>
  );
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
    return `Hectáreas: ${props.hectares}`;
  }
  if (props.level) {
    return `Nivel: ${props.level}`;
  }
  if (props.category) {
    return `Categoría: ${props.category}`;
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
  FIRMS: 'Focos activos',
  NDVI: 'Índice veg.',
  NDMI: 'Humedad veg.',
  ALERTS: 'Alertas',
  REPORTS: 'Reportes',
  WIND: 'Viento',
  HUMIDITY: 'Humedad rel.',
  AIR_TEMP: 'Temp. del aire',
  SOIL_TEMP: 'Temp. del suelo',
  BURNED: 'Área quemada (MapBiomas)',
  LANDCOVER: 'Cobertura (MapBiomas)'
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
  SOIL_TEMP: 'Temperatura del suelo',
  BURNED: 'Área quemada (MapBiomas)',
  LANDCOVER: 'Cobertura del suelo (MapBiomas)'
};

const VISIBLE_RISK_COMPONENTS = new Set(['fwi', 'ndmi', 'firms', 'ndvi', 'reports']);
const POINT_LAYER_INDICATORS = ['FIRMS', 'ALERTS', 'REPORTS'];
// MapBiomas layers that get a year control while visible (Q31).
const MAPBIOMAS_YEAR_CONTROLS = [
  { indicator: 'BURNED', label: 'Área quemada' },
  { indicator: 'LANDCOVER', label: 'Cobertura' }
];

const ALERT_TOOLTIP_COMPONENTS = ['firms', 'fwi', 'ndmi', 'ndvi'];

const REPORT_CATEGORY_LABELS = {
  HUMO: 'Humo',
  FOCO: 'Foco de incendio',
  INCENDIO: 'Incendio activo',
  OTRO: 'Otro'
};
const REPORT_STATUS_LABELS = {
  RECIBIDO: 'Recibido',
  VALIDADO: 'Validado',
  DERIVADO: 'Derivado',
  DESCARTADO: 'Descartado'
};

function ReportCard({ report, pos, onClose }) {
  const [expanded, setExpanded] = useState(false);
  const description = report?.properties?.description;
  useEffect(() => {
    setExpanded(false);
  }, [description]);

  if (!report || !pos) return null;
  const props = report.properties || {};
  const category = props.category || 'OTRO';
  const color = REPORT_CARD_COLORS.category[category] || REPORT_CARD_COLORS.category.OTRO;
  const longDescription = isLongDescription(props.description);
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
      {props.description && (
        <p
          className={`report-card-desc${expanded ? ' report-card-desc--expanded' : ''}`}
          title={longDescription && !expanded ? props.description : undefined}
        >
          {props.description}
        </p>
      )}
      {longDescription && (
        <button
          type="button"
          className="report-card-more"
          aria-expanded={expanded}
          onClick={() => setExpanded((prev) => !prev)}
        >
          {expanded ? 'Ver menos' : 'Ver más'}
        </button>
      )}
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

function ComunaTooltip({ comunaId, nombre, score, pos, burnedLine = '', landcoverLine = '' }) {
  if (!comunaId || !pos) return null;
  const level = ALERT_LEVEL_CONFIG[score?.alertLevel] || ALERT_LEVEL_CONFIG.NORMAL;
  const pct = typeof score?.scoreComposite === 'number' ? (score.scoreComposite * 100).toFixed(0) : null;
  const displayNombre = nombre || score?.nombreComuna || comunaId;
  const mode = score?.mode;
  const trigger = alertTrigger({
    alertLevel: score?.alertLevel,
    score: score?.scoreComposite,
    fwiRaw: score?.fwiRaw,
    firmsCount: score?.firmsCount,
    firmsFrpMean: score?.firmsFrpMean,
    alertDriver: score?.alertDriver
  });

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
            {modeLabel(mode)}
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
          {trigger.kind && trigger.kind !== 'score' && (
            <div className="comuna-tooltip-trigger" style={{ color: level.darkUiColor }}>{trigger.text}</div>
          )}
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
      {burnedLine && <div className="comuna-tooltip-context-line">{burnedLine}</div>}
      {landcoverLine && <div className="comuna-tooltip-context-line">{landcoverLine}</div>}
    </div>
  );
}

// Legend for the BURNED layer. Year, note and source are plain text so colour
// is never the only channel.
function BurnedLegend({ state }) {
  const layer = state?.layer;
  const title = layer ? `Área quemada ${layer.year} (anual)` : 'Área quemada (anual)';
  const note = burnedLegendNote(layer, { hasYearControl: true });

  return (
    <div className="territory-legend-section">
      <h5>{title}</h5>
      {!layer && state?.error && !state?.loading && (
        <div className="territory-meta" role="alert">
          <p>{state.error}</p>
          {state.retry && (
            <button type="button" className="btn btn-secondary" onClick={state.retry}>
              Reintentar
            </button>
          )}
        </div>
      )}
      {!layer && !(state?.error && !state?.loading) && (
        <p className="territory-meta" role="status">
          {state?.loading ? 'Cargando…' : 'Sin datos de Fuego.'}
        </p>
      )}
      {layer && (
        <>
          <ul>
            {BURNED_SCALE.bins.map((bin) => (
              <li key={bin.label}>
                <span className="territory-color-dot" style={{ backgroundColor: bin.color }} />
                <span>{bin.label}</span>
              </li>
            ))}
            <li>
              <span
                className="territory-color-dot"
                style={{ backgroundColor: BURNED_SCALE.zero.color, border: '1px solid #94a3b8' }}
              />
              <span>{BURNED_SCALE.zero.label}</span>
            </li>
            <li>
              <span className="territory-color-dot" style={{ backgroundColor: NEUTRAL_FILL }} />
              <span>Sin datos</span>
            </li>
          </ul>
          {note && <p className="territory-meta">{note}</p>}
          <p className="territory-meta">
            Fuente: {layer.citation || MAPBIOMAS_FIRE_CITATION} Colores: rampa oficial de MapBiomas
            Fuego (tamaño de cicatriz), reutilizada porque no existe una rampa para fracción anual quemada.
          </p>
        </>
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
  onRetry,
  mapbiomasLayers = {},
  readOnly = false
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

  // FIRMS 48 h and reports 7 d come from the regional snapshot components,
  // whose backend windows are exactly 48 h / 7 d (TerritoryRiskServiceImpl).
  const summary = useMemo(() => {
    const comunaNames = {};
    for (const feature of comunalGeoJson?.features || []) {
      const props = feature?.properties;
      if (props?.comunaId && props?.nombre) comunaNames[props.comunaId] = props.nombre;
    }
    return regionalSummary({
      comunaScores: effectiveComunalScores,
      comunaNames,
      comunaTotal: comunalGeoJson?.features?.length,
      firmsCount48h: regionData?.riskScore?.components?.firms?.focosCount,
      reports7d: regionData?.riskScore?.components?.reports?.count
    });
  }, [comunalGeoJson, effectiveComunalScores, regionData]);

  const mapbiomasOwnerId = mapbiomasOwner(visibleIndicators);
  // A previous-year MapBiomas layer is still on screen while a new year loads.
  const mapStale = MAPBIOMAS_YEAR_CONTROLS.some(
    ({ indicator }) => visibleIndicators.includes(indicator) && mapbiomasLayers[indicator]?.stale
  );

  return (
    <article className="dashboard-card territory-map-card">
      {/* One-line regional summary derived from the per-comuna scores the map
          paints, so it never contradicts the choropleth. Always visible when
          regionData is available, independent of the RISK_SCORE toggle. */}
      {regionData && (
        <RegionalSummary
          regionLabel={selectedRegionLabel}
          summary={summary}
          onComunaClick={(comunaId, nombre) =>
            handleComunaClick(comunaId, nombre, effectiveComunalScores?.[comunaId])
          }
        />
      )}

      <div className="filter-bar territory-controls">
        <h3 className="territory-controls-title">Mapa territorial interactivo</h3>
        <label>
          Región
          <select value={selectedRegionId} onChange={(event) => setSelectedRegionId(event.target.value)}>
            {regionOptions.map((region) => (
              <option key={region.id} value={region.id}>
                {region.label}
              </option>
            ))}
          </select>
        </label>

        <LayerToggleGroups
          visibleIndicators={visibleIndicators}
          renderYearControl={(indicator) => {
            const entry = MAPBIOMAS_YEAR_CONTROLS.find((item) => item.indicator === indicator);
            const state = mapbiomasLayers[indicator];
            if (!entry || !controlVisible({ active: visibleIndicators.includes(indicator), state })) return null;
            return <MapbiomasYearControl label={entry.label} state={state} />;
          }}
          renderToggle={(indicator) => (
            <label key={indicator} className="territory-toggle" title={INDICATOR_FULL_LABELS[indicator] || indicator}>
              <input
                type="checkbox"
                aria-label={INDICATOR_FULL_LABELS[indicator] || indicator}
                checked={visibleIndicators.includes(indicator)}
                onChange={() => toggleIndicator(indicator)}
              />
              <span>
                {indicator === 'BURNED' || indicator === 'LANDCOVER'
                  ? chipLabel({
                      plain: INDICATOR_LABELS[indicator],
                      withYear: indicator === 'BURNED'
                        ? burnedToggleLabel(mapbiomasLayers.BURNED?.layer)
                        : landcoverToggleLabel(mapbiomasLayers.LANDCOVER?.layer),
                      controlVisible: controlVisible({
                        active: visibleIndicators.includes(indicator),
                        state: mapbiomasLayers[indicator]
                      })
                    })
                  : INDICATOR_LABELS[indicator] || CLIMATE_SCALES[indicator]?.label || indicator}
              </span>
            </label>
          )}
        />

        <div className="territory-control-actions">
          <button
            type="button"
            className="btn btn-secondary territory-refresh-button"
            onClick={onRetry}
            disabled={loading || refreshing}
          >
            {refreshing ? 'Actualizando...' : 'Actualizar capas'}
          </button>
          {!readOnly && (
            <>
              <WeatherSyncButton regionId={selectedRegionId} />
              <small style={{ color: '#475569', fontSize: '0.8rem', marginTop: 2 }}>
                ⓘ La sincronización puede tardar aprox. 2–3 min
              </small>
            </>
          )}
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
        <EmptyState title="Sin datos territoriales" description="No hay capas para la región seleccionada." />
      ) : null}

      {!loading && !error && regionData ? (
        <div className="territory-map-wrapper">
          <div className="territory-map-region" aria-busy={mapStale}>
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

            {/* Stacking of the MapBiomas layers: RISK_SCORE (410) sits below LANDCOVER
                (412) and BURNED (415) yet owns hover and click when visible. Owner
                precedence (mapbiomasOwner): RISK_SCORE > BURNED > LANDCOVER. Only
                the owner is interactive; the others take no pointer events and
                their data shows as extra lines in the owner's tooltip (the risk
                tooltip, or the BURNED tooltip for LANDCOVER). */}
            <Pane name="landcover-layer-pane" style={{ zIndex: 412 }}>
              {comunalGeoJson && visibleIndicators.includes('LANDCOVER') && (
                <LandcoverChoropleth
                  key={regionData.regionId}
                  geoJson={comunalGeoJson}
                  layer={mapbiomasLayers.LANDCOVER?.layer}
                  loading={Boolean(mapbiomasLayers.LANDCOVER?.loading)}
                  interactive={mapbiomasOwnerId === 'LANDCOVER'}
                  riskVisible={visibleIndicators.includes('RISK_SCORE')}
                  stale={Boolean(mapbiomasLayers.LANDCOVER?.stale)}
                  onComunaClick={handleComunaClick}
                  emphasizeOutline={!mapbiomasLayers.LANDCOVER?.layer && visibleIndicators.every((indicator) => indicator === 'LANDCOVER')}
                />
              )}
            </Pane>

            <Pane name="burned-layer-pane" style={{ zIndex: 415 }}>
              {comunalGeoJson && visibleIndicators.includes('BURNED') && (
                <BurnedChoropleth
                  key={regionData.regionId}
                  geoJson={comunalGeoJson}
                  layer={mapbiomasLayers.BURNED?.layer}
                  loading={Boolean(mapbiomasLayers.BURNED?.loading)}
                  landcoverLayer={visibleIndicators.includes('LANDCOVER') ? (mapbiomasLayers.LANDCOVER?.layer ?? null) : null}
                  interactive={mapbiomasOwnerId === 'BURNED'}
                  stale={Boolean(mapbiomasLayers.BURNED?.stale)}
                  onComunaClick={handleComunaClick}
                  emphasizeOutline={!mapbiomasLayers.BURNED?.layer && visibleIndicators.every((indicator) => indicator === 'BURNED')}
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
          </div>

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
              burnedLine={
                visibleIndicators.includes('BURNED') && mapbiomasLayers.BURNED?.layer
                  ? burnedLineFor(
                      mapbiomasLayers.BURNED.layer.values?.[hoveredComuna.comunaId],
                      mapbiomasLayers.BURNED.layer.year
                    )
                  : ''
              }
              landcoverLine={
                visibleIndicators.includes('LANDCOVER') && mapbiomasLayers.LANDCOVER?.layer
                  ? landcoverLineFor(
                      mapbiomasLayers.LANDCOVER.layer.values?.[hoveredComuna.comunaId],
                      mapbiomasLayers.LANDCOVER.layer.year
                    )
                  : ''
              }
            />
          )}

          {selectedComuna ? (
            <ComunaRiskPanel
              key={selectedComuna.comunaId}
              comunaId={selectedComuna.comunaId}
              comunaName={selectedComuna.nombre}
              openedFrom={comunaPanelOrigin(mapbiomasOwnerId)}
              score={effectiveComunalScores?.[selectedComuna.comunaId] || selectedComuna.score}
              regionId={selectedRegionId}
              onClose={() => setSelectedComuna(null)}
              onScoreUpdated={handleScoreUpdated}
              canSync={false}
              readOnly={readOnly}
              extraSections={
                <ComunaMapbiomasSection
                  comunaId={selectedComuna.comunaId}
                  year={panelYear({
                    visible: visibleIndicators.includes('LANDCOVER'),
                    selectedYear: mapbiomasLayers.LANDCOVER?.selectedYear
                  })}
                  burnedYear={
                    visibleIndicators.includes('BURNED') ? mapbiomasLayers.BURNED?.selectedYear : undefined
                  }
                />
              }
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
                    {riskLegendItems().map((item) => (
                      <li key={item.key} title={item.note}>
                        <span
                          className="territory-risk-swatch"
                          style={{ backgroundColor: item.swatchFill, borderColor: item.borderColor, borderStyle: item.borderStyle }}
                          aria-hidden="true"
                        />
                        <span>{item.text}</span>
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
                      {scale.bins.map((bin, index) => (
                        <li key={bin.label}>
                          <span className="territory-color-dot" style={{ backgroundColor: bin.color }} />
                          <span>{bin.label}</span>
                          {indicator === 'WIND' ? <WindArrowSample size={WIND_ARROW_LEGEND[index].size} /> : null}
                        </li>
                      ))}
                      <li>
                        <span className="territory-color-dot" style={{ backgroundColor: NEUTRAL_FILL }} />
                        <span>Sin datos</span>
                      </li>
                    </ul>
                    {indicator === 'WIND' ? (
                      <p className="territory-legend-note">Las flechas apuntan hacia donde sopla el viento; su tamaño indica la velocidad.</p>
                    ) : null}
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
                      {scale.bins.map((bin, index) => (
                        <li key={bin.label}>
                          <span className="territory-color-dot" style={{ backgroundColor: bin.color }} />
                          <span>{bin.label}</span>
                          {indicator === 'WIND' ? <WindArrowSample size={WIND_ARROW_LEGEND[index].size} /> : null}
                        </li>
                      ))}
                      <li>
                        <span className="territory-color-dot" style={{ backgroundColor: NEUTRAL_FILL }} />
                        <span>Sin datos</span>
                      </li>
                    </ul>
                    {indicator === 'WIND' ? (
                      <p className="territory-legend-note">Las flechas apuntan hacia donde sopla el viento; su tamaño indica la velocidad.</p>
                    ) : null}
                  </div>
                );
              })}

              {visibleIndicators.includes('BURNED') && (
                <BurnedLegend state={mapbiomasLayers.BURNED} />
              )}

              {visibleIndicators.includes('LANDCOVER') && (
                <LandcoverLegend state={mapbiomasLayers.LANDCOVER} />
              )}

              <p className="territory-meta">
                Fuente: {regionData.source === 'backend' ? 'servidor' : 'datos de demostración'} | Última actualización:{' '}
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
