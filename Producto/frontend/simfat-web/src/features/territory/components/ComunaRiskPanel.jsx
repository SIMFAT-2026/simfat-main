import { useEffect, useState } from 'react';
import { LineChart, Line, XAxis, YAxis, Tooltip as ChartTooltip, ResponsiveContainer, ReferenceLine } from 'recharts';
import { fetchComunaHistory } from '../services/territoryApiService';

const ALERT_LEVEL_CONFIG = {
  NORMAL:     { color: '#16a34a', label: 'Normal' },
  PREVENTIVO: { color: '#ca8a04', label: 'Preventivo' },
  ALTO:       { color: '#ea580c', label: 'Alto' },
  CRITICO:    { color: '#dc2626', label: 'Crítico' }
};

const COMPONENT_META = {
  STANDARD: [
    { key: 'fwi',     label: 'FWI meteorológico',  weight: 0.52 },
    { key: 'firms',   label: 'Focos activos',       weight: 0.33 },
    { key: 'reports', label: 'Reportes ciudadanos', weight: 0.15 }
  ],
  ENHANCED: [
    { key: 'fwi',     label: 'FWI meteorológico',  weight: 0.38 },
    { key: 'ndmi',    label: 'Humedad vegetación',  weight: 0.22 },
    { key: 'firms',   label: 'Focos activos',       weight: 0.18 },
    { key: 'loss',    label: 'Cobertura forestal',  weight: 0.10 },
    { key: 'ndvi',    label: 'Índice vegetación',   weight: 0.08 },
    { key: 'reports', label: 'Reportes ciudadanos', weight: 0.04 }
  ]
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
        <ReferenceLine y={50} stroke="#ca8a04" strokeDasharray="3 3" strokeWidth={1} />
        <ReferenceLine y={70} stroke="#ea580c" strokeDasharray="3 3" strokeWidth={1} />
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

export default function ComunaRiskPanel({ comunaId, score, onClose, canSync, onSync }) {
  if (!comunaId || !score) return null;

  const level = ALERT_LEVEL_CONFIG[score.alertLevel] || ALERT_LEVEL_CONFIG.NORMAL;
  const pct = typeof score.scoreComposite === 'number' ? (score.scoreComposite * 100).toFixed(0) : '—';
  const mode = score.mode || 'STANDARD';
  const components = score.components || {};
  const meta = COMPONENT_META[mode] || COMPONENT_META.STANDARD;

  const computedAt = score.computedAt
    ? (() => { const u = /Z|[+-]\d{2}:?\d{2}$/.test(score.computedAt) ? score.computedAt : score.computedAt + 'Z'; return new Date(u).toLocaleString('es-CL', { timeZone: 'America/Santiago', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }); })()
    : null;

  return (
    <aside className="comuna-risk-panel">
      <div className="panel-header">
        <div>
          <p className="panel-region">{score.regionId}</p>
          <h4 className="panel-nombre">{score.nombreComuna || comunaId}</h4>
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
          {score.qualityFlag && (
            <span className="panel-quality-flag" title={score.qualityFlag}>⚠ parcial</span>
          )}
        </div>
      </div>

      <section className="panel-section">
        <h5 className="panel-section-title">Componentes WLC</h5>
        <div className="panel-components">
          {meta.map(({ key, label, weight }) => {
            const val = components[key];
            const compPct = typeof val === 'number' ? Math.round(val * 100) : null;
            const barWidth = compPct != null ? Math.min(compPct, 100) : 0;
            return (
              <div key={key} className="panel-component-row">
                <span className="panel-comp-label">{label}</span>
                <div className="panel-comp-bar-wrap">
                  <div
                    className="panel-comp-bar"
                    style={{ width: `${barWidth}%`, backgroundColor: level.color }}
                  />
                </div>
                <span className="panel-comp-val">{compPct != null ? compPct : '—'}</span>
                <span className="panel-comp-weight">{(weight * 100).toFixed(0)}%</span>
              </div>
            );
          })}
        </div>
      </section>

      <section className="panel-section">
        <h5 className="panel-section-title">Componentes FWI proxy</h5>
        <FwiInputsBreakdown fwiInputs={score.fwiInputs} />
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
      </div>
    </aside>
  );
}
