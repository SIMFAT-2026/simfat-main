import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { LineChart, Line, XAxis, YAxis, Tooltip as ChartTooltip, ResponsiveContainer, ReferenceLine } from 'recharts';
import { fetchComunaHistory, fetchComunalRiskScores, syncComunaCopernicus } from '../services/territoryApiService';
import { ALERT_LEVEL_CONFIG } from '../constants/colorScales';
import { generateComunalReport } from '../utils/reportPrint';


const COMPONENT_META = {
  STANDARD: [
    { key: 'fwi',     label: 'FWI meteorológico',        weight: 0.52 },
    { key: 'firms',   label: 'Focos activos',             weight: 0.33 },
    { key: 'reports', label: 'Reportes ciudadanos',       weight: 0.15 }
  ],
  ENHANCED: [
    { key: 'fwi',     label: 'FWI meteorológico',        weight: 0.38 },
    { key: 'ndmi',    label: 'Humedad vegetación (NDMI)', weight: 0.22 },
    { key: 'firms',   label: 'Focos activos',             weight: 0.18 },
    { key: 'ndvi',    label: 'Índice vegetación (NDVI)',  weight: 0.08 },
    { key: 'reports', label: 'Reportes ciudadanos',       weight: 0.04 }
  ]
};

const COMPONENT_INFO = {
  fwi: 'Índice de Peligro de Incendio (FWI). Escala 0–100+:\n• 0–11: Bajo\n• 11–21: Moderado\n• 21–38: Alto\n• 38–50: Muy alto\n• >50: Extremo',
  ndmi: 'NDMI — Humedad de la vegetación. Rango -1 a 1:\n• >0.1: Vegetación húmeda, bajo riesgo\n• -0.1 a 0.1: Estrés hídrico leve\n• <-0.1: Estrés hídrico severo, alto riesgo',
  ndvi: 'NDVI — Índice de vegetación. Rango -1 a 1:\n• >0.5: Vegetación densa y sana\n• 0.2–0.5: Vegetación moderada\n• 0–0.2: Vegetación escasa o suelo desnudo\n• <0: Superficie no vegetada (agua, suelo expuesto)',
  firms: 'Focos activos detectados por satélite NASA (FIRMS) en las últimas 24 h. A mayor número de focos de alta confianza, mayor riesgo inmediato.',
  reports: 'Reportes ciudadanos verificados de humo, focos o incendios activos en la comuna. Complementa los datos satelitales con observación en terreno.'
};

function levelColor(level) {
  return (ALERT_LEVEL_CONFIG[level] || ALERT_LEVEL_CONFIG.NORMAL).color;
}

function ChartDot({ cx, cy, payload }) {
  const color = levelColor(payload?.alertLevel);
  return <circle cx={cx} cy={cy} r={4} fill={color} stroke="#0f172a" strokeWidth={1} />;
}

function RiskHistoryChart({ gadmGid }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    fetchComunaHistory(gadmGid, 30)
      .then((res) => {
        if (!cancelled) {
          const snaps = Array.isArray(res?.snapshots) ? res.snapshots : [];
          const chartData = [...snaps]
            .reverse()
            .map((s) => ({
              date: s.computedAt ? new Date(s.computedAt).toLocaleDateString('es-CL', { day: '2-digit', month: '2-digit' }) : '—',
              score: typeof s.scoreComposite === 'number' ? Math.round(s.scoreComposite * 100) : null,
              alertLevel: s.alertLevel || 'NORMAL',
              mode: s.mode
            }));
          setData(chartData);
        }
      })
      .catch(() => { if (!cancelled) setData([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [gadmGid]);

  if (loading) return <p className="panel-chart-empty">Cargando historial…</p>;
  if (!data || data.length === 0) return <p className="panel-chart-empty">Sin historial disponible aún.</p>;

  return (
    <ResponsiveContainer width="100%" height={110}>
      <LineChart data={data} margin={{ top: 8, right: 8, left: -20, bottom: 0 }}>
        <XAxis dataKey="date" tick={{ fontSize: 10, fill: '#64748b' }} tickLine={false} axisLine={false} />
        <YAxis domain={[0, 100]} tick={{ fontSize: 10, fill: '#64748b' }} tickLine={false} axisLine={false} />
        <ChartTooltip
          contentStyle={{ background: '#1e293b', border: 'none', borderRadius: 8, fontSize: 11, color: '#f1f5f9' }}
          formatter={(val, _name, props) => [`${val}/100 ${props?.payload?.mode === 'ENHANCED' ? '✦' : ''}`, 'Score']}
        />
        <ReferenceLine y={50} stroke={ALERT_LEVEL_CONFIG.PREVENTIVO.color} strokeDasharray="3 3" strokeWidth={1} />
        <ReferenceLine y={70} stroke={ALERT_LEVEL_CONFIG.ALTO.color} strokeDasharray="3 3" strokeWidth={1} />
        <Line
          type="monotone"
          dataKey="score"
          stroke="#38bdf8"
          strokeWidth={2}
          dot={<ChartDot />}
          activeDot={{ r: 6 }}
          connectNulls
        />
      </LineChart>
    </ResponsiveContainer>
  );
}

// Open-Meteo inputs feeding the FWI proxy (transparency view, spec
// fwi-proxy-breakdown). Any component may be null — rendered as "—" without
// breaking the layout, and never affects the FWI proxy value shown elsewhere.
const FWI_INPUT_META = [
  { key: 'tempMax', label: 'Temp. máxima', unit: '°C' },
  { key: 'humidityMin', label: 'Humedad mínima', unit: '%' },
  { key: 'windMax', label: 'Viento máximo', unit: 'km/h' },
  { key: 'precip', label: 'Precipitación', unit: 'mm' },
  { key: 'soilTemp', label: 'Temp. del suelo', unit: '°C' }
];

function FwiInputsBreakdown({ fwiInputs }) {
  if (!fwiInputs) {
    return <p className="panel-chart-empty">Sin datos de componentes FWI disponibles.</p>;
  }

  return (
    <div className="panel-fwi-inputs">
      {FWI_INPUT_META.map(({ key, label, unit }) => {
        const val = fwiInputs[key];
        const display = typeof val === 'number' ? `${val.toFixed(1)} ${unit}` : '—';
        return (
          <div key={key} className="panel-fwi-input-row">
            <span className="panel-fwi-input-label">{label}</span>
            <span className="panel-fwi-input-value">{display}</span>
          </div>
        );
      })}
    </div>
  );
}

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

const COPERNICUS_WAIT_S = 75;
const COPERNICUS_RETRY_WAIT_S = 35;

export default function ComunaRiskPanel({ comunaId, score, regionId, onClose, canSync, onSync, onScoreUpdated }) {
  const [syncState, setSyncState] = useState({ phase: 'idle', countdown: 0, error: null, result: null, retries: 0 });

  useEffect(() => {
    if (syncState.phase !== 'waiting') return;
    if (syncState.countdown <= 0) {
      setSyncState((s) => ({ ...s, phase: 'fetching' }));
      fetchComunalRiskScores(regionId)
        .then((scores) => {
          const updated = scores[comunaId];
          const isEnhanced = updated?.mode === 'ENHANCED' && Number.isFinite(updated?.scoreComposite);
          if (isEnhanced) {
            setSyncState({ phase: 'done', countdown: 0, error: null, result: updated, retries: 0 });
            onScoreUpdated?.(updated);
          } else {
            setSyncState((s) => {
              if (s.retries < 1) {
                return { phase: 'waiting', countdown: COPERNICUS_RETRY_WAIT_S, error: null, result: null, retries: s.retries + 1 };
              }
              const noData = 'Copernicus no devolvió datos de vegetación para esta área (posible nubosidad o sin cobertura reciente).';
              return { phase: 'done', countdown: 0, error: noData, result: null, retries: 0 };
            });
          }
        })
        .catch(() => {
          setSyncState({ phase: 'done', countdown: 0, error: 'Error al obtener el score actualizado', result: null, retries: 0 });
        });
      return;
    }
    const timer = setTimeout(() => {
      setSyncState((s) => s.phase === 'waiting' ? { ...s, countdown: s.countdown - 1 } : s);
    }, 1000);
    return () => clearTimeout(timer);
  }, [syncState.phase, syncState.countdown, comunaId, regionId]);

  if (!comunaId || !score) return null;

  const displayScore = syncState.result || score;
  const level = ALERT_LEVEL_CONFIG[displayScore.alertLevel] || ALERT_LEVEL_CONFIG.NORMAL;
  const pct = typeof displayScore.scoreComposite === 'number' ? (displayScore.scoreComposite * 100).toFixed(0) : '—';
  const mode = displayScore.mode || 'STANDARD';
  const components = displayScore.components || {};
  const meta = COMPONENT_META[mode] || COMPONENT_META.STANDARD;

  const computedAt = displayScore.computedAt
    ? (() => { const u = /Z|[+-]\d{2}:?\d{2}$/.test(displayScore.computedAt) ? displayScore.computedAt : displayScore.computedAt + 'Z'; return new Date(u).toLocaleString('es-CL', { timeZone: 'America/Santiago', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }); })()
    : null;

  function handleExportComunal() {
    const regionLabel = displayScore.regionId || regionId || '';
    generateComunalReport({ score: displayScore, comunaId, regionLabel, generatedAt: displayScore.computedAt });
  }

  async function handleCopernicusSync() {
    setSyncState({ phase: 'requesting', countdown: 0, error: null, result: null, retries: 0 });
    try {
      await syncComunaCopernicus(comunaId);
      setSyncState({ phase: 'waiting', countdown: COPERNICUS_WAIT_S, error: null, result: null, retries: 0 });
    } catch (err) {
      const msg = err?.response?.data?.message || err?.message || 'Error al conectar con Copernicus';
      setSyncState({ phase: 'done', countdown: 0, error: msg, result: null, retries: 0 });
    }
  }

  const isLoading = syncState.phase === 'requesting' || syncState.phase === 'waiting' || syncState.phase === 'fetching';
  const btnLabel = syncState.phase === 'requesting' ? 'Iniciando…'
    : syncState.phase === 'waiting' && syncState.retries === 0 ? `Copernicus procesando… ${syncState.countdown}s`
    : syncState.phase === 'waiting' && syncState.retries > 0 ? `Verificando resultado… ${syncState.countdown}s`
    : syncState.phase === 'fetching' ? 'Obteniendo resultado…'
    : 'Confirmar con Copernicus';

  return (
    <aside className="comuna-risk-panel">
      <div className="panel-header">
        <div>
          <p className="panel-region">{displayScore.regionId}</p>
          <h4 className="panel-nombre">{displayScore.nombreComuna || comunaId}</h4>
        </div>
        <button type="button" className="panel-close" onClick={onClose} aria-label="Cerrar">×</button>
      </div>

      <div className="panel-score-row" style={{ borderColor: level.color }}>
        <div>
          <span className="panel-level" style={{ color: level.color }}>{level.label}</span>
          <span className="panel-pct" style={{ color: level.color }}>{pct}<small>/100</small></span>
        </div>
        <div className="panel-badges">
          <span className={`panel-mode-badge ${mode === 'ENHANCED' ? 'enhanced' : 'standard'}`}>{mode}</span>
          {displayScore.qualityFlag && (
            <span className="panel-quality-flag" title={displayScore.qualityFlag}>⚠ parcial</span>
          )}
        </div>
      </div>

      <section className="panel-section">
        <h5 className="panel-section-title">Componentes WLC</h5>
        <div className="panel-components">
          {meta.map(({ key, label, weight }) => {
            const val = components[key];
            const indicatorPct = typeof val === 'number' ? Math.round((val / weight) * 100) : null;
            const barWidth = indicatorPct != null ? Math.min(indicatorPct, 100) : 0;
            const info = COMPONENT_INFO[key];
            return (
              <div key={key} className="panel-component-row">
                <span className="panel-comp-label">{label}</span>
                {info ? <IndexInfo info={info} label={label} /> : <span />}
                <div className="panel-comp-bar-wrap">
                  <div
                    className="panel-comp-bar"
                    style={{ width: `${barWidth}%`, backgroundColor: level.color }}
                  />
                </div>
                <span className="panel-comp-val">{indicatorPct != null ? indicatorPct : '—'}</span>
                <span className="panel-comp-weight">{(weight * 100).toFixed(0)}%</span>
              </div>
            );
          })}
        </div>
      </section>

      <section className="panel-section">
        <h5 className="panel-section-title">Componentes FWI proxy</h5>
        <FwiInputsBreakdown fwiInputs={displayScore.fwiInputs} />
      </section>

      <section className="panel-section">
        <h5 className="panel-section-title">Evolución 30 días</h5>
        <RiskHistoryChart gadmGid={comunaId} />
        <p className="panel-chart-legend">
          <span style={{ borderBottom: '2px dashed #ca8a04' }}>&nbsp;&nbsp;</span> Preventivo &nbsp;
          <span style={{ borderBottom: '2px dashed #ea580c' }}>&nbsp;&nbsp;</span> Alto &nbsp;
          ✦ ENHANCED
        </p>
      </section>

      <div className="panel-footer">
        {computedAt && <span className="panel-meta">Actualizado {computedAt}</span>}
        {canSync && (
          <button type="button" className="btn btn-secondary btn-sm panel-sync-btn" onClick={onSync}>
            Sync ahora
          </button>
        )}
        <button
          type="button"
          className="btn btn-primary btn-sm panel-copernicus-btn"
          onClick={handleCopernicusSync}
          disabled={isLoading}
          title="Consulta NDVI y NDMI directamente desde Copernicus para esta comuna (~70s)"
        >
          {btnLabel}
        </button>
        <button
          type="button"
          className="btn btn-secondary btn-sm"
          onClick={handleExportComunal}
          title="Exportar informe comunal en PDF"
        >
          Exportar informe
        </button>
        {syncState.phase === 'done' && syncState.result && (
          <span className="panel-sync-ok">
            NDVI {typeof syncState.result.ndviRaw === 'number' ? syncState.result.ndviRaw.toFixed(3) : '—'} ·
            NDMI {typeof syncState.result.ndmiRaw === 'number' ? syncState.result.ndmiRaw.toFixed(3) : '—'} ·
            {syncState.result.mode}
          </span>
        )}
        {syncState.phase === 'done' && syncState.error && (
          <span className="panel-sync-error" title={syncState.error}>Error Copernicus</span>
        )}
      </div>
    </aside>
  );
}
