import React from 'react';
import { PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import SectionTitle from '../components/SectionTitle';
import { useTerritoryLayers } from '../features/territory/hooks/useTerritoryLayers';
import {
  ALERT_LEVEL_CONFIG,
  CLIMATE_SCALES,
  VEGETATION_SCALES
} from '../features/territory/constants/colorScales';
import { generateRegionalReport } from '../features/territory/utils/reportPrint';
import '../features/dashboard/dashboard.css';

// ─── Types ───────────────────────────────────────────────────────────────────

interface DashboardPageProps {
  selectedRegionId?: string;
}

interface GeoFeature {
  type: 'Feature';
  id?: string | number;
  properties: Record<string, unknown>;
  geometry: unknown;
}

interface GeoLayer {
  type?: string;
  features?: GeoFeature[];
  indicator?: string;
  unit?: string;
  values?: Record<string, unknown>;
}

interface RiskComponent {
  score?: number;
  weight?: number;
  rawValue?: number;
  focosCount?: number;
  count?: number;
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

function getVegetationLabel(value: number | null | undefined, indicator: 'NDVI' | 'NDMI'): string {
  if (value == null || !isFinite(value)) return '—';
  const scale = VEGETATION_SCALES[indicator];
  const bin = scale.bins.find((b) => value < b.max);
  return bin ? bin.label : scale.bins[scale.bins.length - 1].label;
}

function getClimateColor(value: number, indicator: 'WIND' | 'HUMIDITY' | 'AIR_TEMP' | 'SOIL_TEMP'): string {
  const scale = CLIMATE_SCALES[indicator];
  const bin = scale.bins.find((b) => value < b.max);
  return bin ? bin.color : scale.bins[scale.bins.length - 1].color;
}

// Climate values shape: { comunaId: { value, unit, observedAt } }
// (mock uses empty {}, so we always get null there gracefully)
function climateAvg(values: Record<string, unknown>): number | null {
  const nums = Object.values(values ?? {}).map((v) => {
    if (typeof v === 'number') return v;
    if (typeof v === 'object' && v !== null) return (v as { value?: number }).value ?? null;
    return null;
  }).filter((v): v is number => typeof v === 'number' && isFinite(v));
  return nums.length ? nums.reduce((s, n) => s + n, 0) / nums.length : null;
}

// ─── FIRMS bbox filter ────────────────────────────────────────────────────────

function extractGeoJsonBbox(geoJson: GeoLayer | null): [number, number, number, number] | null {
  const features = geoJson?.features;
  if (!features?.length) return null;

  let minLng = Infinity, maxLng = -Infinity;
  let minLat = Infinity, maxLat = -Infinity;

  function visitCoords(c: unknown): void {
    if (!Array.isArray(c)) return;
    if (typeof c[0] === 'number' && typeof c[1] === 'number') {
      const lng = c[0] as number, lat = c[1] as number;
      if (lng < minLng) minLng = lng;
      if (lng > maxLng) maxLng = lng;
      if (lat < minLat) minLat = lat;
      if (lat > maxLat) maxLat = lat;
    } else {
      (c as unknown[]).forEach(visitCoords);
    }
  }

  for (const feat of features) {
    visitCoords((feat.geometry as { coordinates?: unknown } | null)?.coordinates);
  }

  return isFinite(minLng) ? [minLng, minLat, maxLng, maxLat] : null;
}

function filterByBbox(features: GeoFeature[], bbox: [number, number, number, number] | null): GeoFeature[] {
  if (!bbox) return features;
  const [minLng, minLat, maxLng, maxLat] = bbox;
  return features.filter((f) => {
    const geom = f.geometry as { type?: string; coordinates?: number[] } | null;
    if (geom?.type !== 'Point' || !Array.isArray(geom.coordinates)) return true;
    const [lng, lat] = geom.coordinates;
    return typeof lng === 'number' && typeof lat === 'number'
      && lng >= minLng && lng <= maxLng
      && lat >= minLat && lat <= maxLat;
  });
}

// ─── Style tokens ─────────────────────────────────────────────────────────────

const panel: React.CSSProperties = {
  border: '1px solid #e2e8f0',
  borderRadius: 10,
  padding: '12px 14px',
  background: '#fff',
};

const panelTitle: React.CSSProperties = {
  margin: '0 0 10px',
  fontSize: '0.88rem',
  fontWeight: 600,
  color: '#1e293b',
};

const statCard: React.CSSProperties = {
  border: '1px solid #e2e8f0',
  borderRadius: 8,
  padding: '8px 10px',
  display: 'flex',
  flexDirection: 'column',
  gap: 2,
  background: '#f8fafc',
};

const label12: React.CSSProperties = { fontSize: '0.72rem', fontWeight: 600, color: '#64748b', letterSpacing: '0.04em' };
const val20: React.CSSProperties = { fontSize: '1.3rem', fontWeight: 700, color: '#0f172a' };

// ─── Skeleton ─────────────────────────────────────────────────────────────────

function SkeletonBlock({ lines = 3 }: { lines?: number }) {
  return (
    <div className="dashboard-skeleton">
      {Array.from({ length: lines }).map((_, i) => (
        <div
          key={i}
          className={`dashboard-skeleton-line${i === 0 ? ' dashboard-skeleton-line-lg' : ''}`}
          style={{ width: i === 0 ? '60%' : `${75 - i * 10}%` }}
        />
      ))}
    </div>
  );
}

// ─── FIRMS Panel ──────────────────────────────────────────────────────────────

interface FirmsProperties { frp?: number; acqDate?: string; }

function FirmsPanel({ features }: { features: GeoFeature[] }) {
  const today = new Date().toISOString().slice(0, 10);
  const todayCount = features.filter((f) => {
    const d = (f.properties as FirmsProperties)?.acqDate;
    return typeof d === 'string' && d.slice(0, 10) === today;
  }).length;
  const highFrpCount = features.filter((f) => {
    const frp = (f.properties as FirmsProperties)?.frp;
    return typeof frp === 'number' && frp > 50;
  }).length;

  return (
    <div style={panel}>
      <h3 style={panelTitle}>Focos FIRMS (NASA)</h3>
      <p style={{ fontSize: 11, color: '#888', margin: '0 0 8px' }}>
        Vista regional bruta (sin atribución por comuna) · ventana visible en el mapa
      </p>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8 }}>
        {[
          { lbl: 'TOTAL ACTIVOS',   val: features.length, color: undefined },
          { lbl: 'DETECTADOS HOY',  val: todayCount,       color: undefined },
          { lbl: 'FRP > 50 MW',     val: highFrpCount,     color: highFrpCount > 0 ? '#b45309' : undefined },
        ].map(({ lbl, val, color }) => (
          <div key={lbl} style={statCard}>
            <span style={label12}>{lbl}</span>
            <span style={{ ...val20, color: color || '#0f172a' }}>{val}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── Alerts Panel (donut) ─────────────────────────────────────────────────────

const ALERT_LEVELS = ['PREVENTIVO', 'ALTO', 'CRITICO'] as const;

function AlertsPanel({ features }: { features: GeoFeature[] }) {
  const counts: Record<string, number> = { PREVENTIVO: 0, ALTO: 0, CRITICO: 0 };
  for (const feat of features) {
    const level = (feat.properties as { level?: string })?.level;
    if (level && level in counts) counts[level]++;
  }
  const data = ALERT_LEVELS.map((lvl) => ({
    name: ALERT_LEVEL_CONFIG[lvl].label,
    value: counts[lvl],
    color: ALERT_LEVEL_CONFIG[lvl].color,
  })).filter((d) => d.value > 0);
  const total = ALERT_LEVELS.reduce((s, l) => s + counts[l], 0);

  return (
    <div style={panel}>
      <h3 style={panelTitle}>Alertas activas</h3>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div style={{ flexShrink: 0, width: 90, height: 90 }}>
          {total > 0 ? (
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={data} cx="50%" cy="50%" innerRadius={22} outerRadius={40} dataKey="value" stroke="none">
                  {data.map((entry) => <Cell key={entry.name} fill={entry.color} />)}
                </Pie>
                <Tooltip formatter={(v: number) => [v, '']} />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <div style={{ width: 90, height: 90, borderRadius: '50%', background: '#f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <span style={{ fontSize: '0.7rem', color: '#94a3b8' }}>Sin alertas</span>
            </div>
          )}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 5, flex: 1 }}>
          {ALERT_LEVELS.map((lvl) => {
            const cfg = ALERT_LEVEL_CONFIG[lvl];
            return (
              <div key={lvl} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <div style={{ width: 10, height: 10, borderRadius: 2, background: cfg.color, flexShrink: 0 }} />
                <span style={{ fontSize: '0.76rem', color: '#334155', flex: 1 }}>{cfg.label}</span>
                <span style={{ fontSize: '0.8rem', fontWeight: 700, color: cfg.color }}>{counts[lvl]}</span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

// ─── Citizen Reports Panel (bar chart) ───────────────────────────────────────

const REPORT_CATS = ['HUMO', 'FOCO', 'INCENDIO', 'OTRO'] as const;
const REPORT_COLORS: Record<string, string> = {
  HUMO: '#f59e0b', FOCO: '#f97316', INCENDIO: '#dc2626', OTRO: '#94a3b8',
};

function CitizenReportsPanel({ features }: { features: GeoFeature[] }) {
  const counts: Record<string, number> = { HUMO: 0, FOCO: 0, INCENDIO: 0, OTRO: 0 };
  for (const feat of features) {
    const cat = (feat.properties as { category?: string })?.category || 'OTRO';
    if (cat in counts) counts[cat]++;
    else counts.OTRO++;
  }
  const data = REPORT_CATS.map((cat) => ({ name: cat, value: counts[cat], fill: REPORT_COLORS[cat] }));

  return (
    <div style={panel}>
      <h3 style={panelTitle}>Reportes ciudadanos</h3>
      <ResponsiveContainer width="100%" height={90}>
        <BarChart data={data} barSize={28} margin={{ top: 4, right: 4, left: -24, bottom: 0 }}>
          <XAxis dataKey="name" tick={{ fontSize: 10 }} axisLine={false} tickLine={false} />
          <YAxis tick={{ fontSize: 10 }} allowDecimals={false} />
          <Tooltip formatter={(v: number) => [v, 'reportes']} />
          <Bar dataKey="value" radius={[4, 4, 0, 0]}>
            {data.map((entry) => <Cell key={entry.name} fill={entry.fill} />)}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

// ─── Vegetation Panel ─────────────────────────────────────────────────────────

function VegetationPanel({ ndviLayer, ndmiLayer }: { ndviLayer: GeoLayer | null; ndmiLayer: GeoLayer | null }) {
  function extractAvg(layer: GeoLayer | null): number | null {
    if (!layer?.features?.length) return null;
    const nums = layer.features
      .map((f) => (f.properties as { value?: number })?.value)
      .filter((v): v is number => typeof v === 'number');
    return nums.length ? nums.reduce((s, n) => s + n, 0) / nums.length : null;
  }
  const ndvi = extractAvg(ndviLayer);
  const ndmi = extractAvg(ndmiLayer);

  return (
    <div style={panel}>
      <h3 style={panelTitle}>Vegetación</h3>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
        {([['NDVI', ndvi], ['NDMI', ndmi]] as const).map(([ind, val]) => (
          <div key={ind} style={statCard}>
            <span style={label12}>{ind}</span>
            <span style={{ ...val20, fontSize: '1.1rem' }}>{val != null ? val.toFixed(3) : '—'}</span>
            <span style={{ fontSize: '0.7rem', color: '#64748b' }}>{getVegetationLabel(val, ind)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── Climate Panel ────────────────────────────────────────────────────────────

interface ClimateLayer { unit?: string; values?: Record<string, unknown>; }

const CLIMATE_CARDS = [
  { key: 'WIND',     indicator: 'WIND'     as const, label: 'Viento' },
  { key: 'HUMIDITY', indicator: 'HUMIDITY' as const, label: 'Humedad rel.' },
  { key: 'AIR_TEMP', indicator: 'AIR_TEMP' as const, label: 'Temp. aire' },
  { key: 'SOIL_TEMP',indicator: 'SOIL_TEMP'as const, label: 'Temp. suelo' },
];

function ClimatePanel({ layers }: { layers: Record<string, ClimateLayer> }) {
  return (
    <div style={panel}>
      <h3 style={panelTitle}>Clima actual</h3>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 8 }}>
        {CLIMATE_CARDS.map(({ key, indicator, label }) => {
          const values = (layers[key] as ClimateLayer | undefined)?.values || {};
          const unit = (layers[key] as ClimateLayer | undefined)?.unit || CLIMATE_SCALES[indicator].unit;
          const val = climateAvg(values);
          const color = val != null ? getClimateColor(val, indicator) : '#94a3b8';
          return (
            <div key={key} style={{ ...statCard, borderLeft: `3px solid ${color}` }}>
              <span style={label12}>{label.toUpperCase()}</span>
              <span style={{ ...val20, fontSize: '1.1rem' }}>{val != null ? val.toFixed(1) : '—'}</span>
              <span style={{ fontSize: '0.7rem', color: '#64748b' }}>{unit}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ─── Report Data ─────────────────────────────────────────────────────────────

interface ReportData {
  regionLabel: string;
  generatedAt: string | null;
  alertLevel: string;
  score: number | null;
  firms: { total: number; today: number; highFrp: number };
  alerts: { PREVENTIVO: number; ALTO: number; CRITICO: number };
  reports: { HUMO: number; FOCO: number; INCENDIO: number; OTRO: number };
  ndvi: number | null;
  ndmiVal: number | null;
  ndviLabel: string;
  ndmiLabel: string;
  wind: number | null;
  humidity: number | null;
  airTemp: number | null;
  soilTemp: number | null;
}

function buildReportData(
  regionLabel: string,
  selectedRegionData: Record<string, unknown> | null,
  layers: Record<string, GeoLayer> | null,
  regionFilteredFirms?: GeoFeature[],
): ReportData {
  const rs = selectedRegionData?.riskScore as { scoreComposite?: number; alertLevel?: string } | null ?? null;
  const alertLevel = rs?.alertLevel || 'NORMAL';
  const score = typeof rs?.scoreComposite === 'number' ? Math.round(rs.scoreComposite * 100) : null;

  const today = new Date().toISOString().slice(0, 10);
  const firmsFeatures = regionFilteredFirms ?? layers?.FIRMS?.features ?? [];
  const firms = {
    total: firmsFeatures.length,
    today: firmsFeatures.filter((f) => {
      const d = (f.properties as { acqDate?: string })?.acqDate;
      return typeof d === 'string' && d.slice(0, 10) === today;
    }).length,
    highFrp: firmsFeatures.filter((f) => {
      const frp = (f.properties as { frp?: number })?.frp;
      return typeof frp === 'number' && frp > 50;
    }).length,
  };

  const alertCounts = { PREVENTIVO: 0, ALTO: 0, CRITICO: 0 };
  for (const f of layers?.ALERTS?.features || []) {
    const lvl = (f.properties as { level?: string })?.level;
    if (lvl && lvl in alertCounts) alertCounts[lvl as keyof typeof alertCounts]++;
  }

  const reportCounts = { HUMO: 0, FOCO: 0, INCENDIO: 0, OTRO: 0 };
  for (const f of layers?.REPORTS?.features || []) {
    const cat = (f.properties as { category?: string })?.category || 'OTRO';
    if (cat in reportCounts) reportCounts[cat as keyof typeof reportCounts]++;
    else reportCounts.OTRO++;
  }

  function extractAvg(layer: GeoLayer | null) {
    const nums = (layer?.features || [])
      .map((f) => (f.properties as { value?: number })?.value)
      .filter((v): v is number => typeof v === 'number');
    return nums.length ? nums.reduce((s, n) => s + n, 0) / nums.length : null;
  }

  function climateVal(key: string) {
    const values = (layers?.[key] as ClimateLayer | undefined)?.values || {};
    return climateAvg(values);
  }

  const ndvi = extractAvg(layers?.NDVI ?? null);
  const ndmiVal = extractAvg(layers?.NDMI ?? null);

  return {
    regionLabel,
    generatedAt: (selectedRegionData?.generatedAt as string | null) ?? null,
    alertLevel,
    score,
    firms,
    alerts: alertCounts,
    reports: reportCounts,
    ndvi,
    ndmiVal,
    ndviLabel: getVegetationLabel(ndvi, 'NDVI'),
    ndmiLabel: getVegetationLabel(ndmiVal, 'NDMI'),
    wind: climateVal('WIND'),
    humidity: climateVal('HUMIDITY'),
    airTemp: climateVal('AIR_TEMP'),
    soilTemp: climateVal('SOIL_TEMP'),
  };
}

// ─── DashboardPage ────────────────────────────────────────────────────────────

function DashboardPage({ selectedRegionId }: DashboardPageProps) {
  const {
    regionOptions,
    selectedRegionId: regionId,
    selectedRegionData,
    loading,
    refreshing,
    reloadSelectedRegion,
  } = useTerritoryLayers({
    initialRegionId: selectedRegionId,
    initialVisibleIndicators: ['RISK_SCORE', 'FIRMS', 'ALERTS', 'REPORTS', 'NDVI', 'NDMI', 'WIND', 'HUMIDITY', 'AIR_TEMP', 'SOIL_TEMP'],
  });

  const layers = (selectedRegionData?.layers || null) as Record<string, GeoLayer> | null;
  const regionLabel = regionOptions.find((r) => r.id === regionId)?.label || regionId;
  const isLoading = loading;

  const comunalGeoJson = (selectedRegionData as Record<string, unknown> | null)?.comunalGeoJson as GeoLayer | null;
  const regionBbox = React.useMemo(() => extractGeoJsonBbox(comunalGeoJson), [comunalGeoJson]);
  const firmsFeatures = React.useMemo(
    () => filterByBbox(layers?.FIRMS?.features || [], regionBbox),
    [layers, regionBbox],
  );

  function handleExport() {
    const data = buildReportData(regionLabel, selectedRegionData as Record<string, unknown> | null, layers, firmsFeatures);
    generateRegionalReport(data);
  }

  return (
    <section className="page-container">
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
        <SectionTitle
          title={`Panel analítico — ${regionLabel}`}
          subtitle="Resumen de alertas, focos y variables ambientales"
        />
        <button
          type="button"
          className="btn btn-secondary"
          onClick={reloadSelectedRegion}
          disabled={refreshing || isLoading}
          style={{ fontSize: '0.8rem', alignSelf: 'flex-start' }}
        >
          {refreshing ? 'Actualizando…' : 'Actualizar datos'}
        </button>
      </div>

      {/* A–C. FIRMS · Alerts · Reports */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 10 }}>
        {isLoading ? (
          <><div style={panel}><SkeletonBlock /></div><div style={panel}><SkeletonBlock /></div><div style={panel}><SkeletonBlock /></div></>
        ) : (
          <>
            <FirmsPanel features={firmsFeatures} />
            <AlertsPanel features={layers?.ALERTS?.features || []} />
            <CitizenReportsPanel features={layers?.REPORTS?.features || []} />
          </>
        )}
      </div>

      {/* D–E. Vegetation · Climate */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 10, marginTop: 10 }}>
        {isLoading ? (
          <><div style={panel}><SkeletonBlock /></div><div style={panel}><SkeletonBlock /></div></>
        ) : (
          <>
            <VegetationPanel ndviLayer={layers?.NDVI ?? null} ndmiLayer={layers?.NDMI ?? null} />
            <ClimatePanel layers={(layers as Record<string, ClimateLayer>) || {}} />
          </>
        )}
      </div>

      {selectedRegionData?.generatedAt && (
        <div style={{ margin: '10px 0 0', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 10 }}>
          <p style={{ fontSize: '0.72rem', color: '#94a3b8' }}>
            Datos generados:{' '}
            {new Date(selectedRegionData.generatedAt).toLocaleString('es-CL', {
              timeZone: 'America/Santiago', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
            })}
            {selectedRegionData.source === 'mock' && ' (modo demo)'}
          </p>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={handleExport}
            disabled={!selectedRegionData}
            style={{ fontSize: '0.75rem' }}
          >
            Exportar informe regional
          </button>
        </div>
      )}
    </section>
  );
}

export default DashboardPage;
