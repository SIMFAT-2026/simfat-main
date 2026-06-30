import { ALERT_LEVEL_CONFIG, VEGETATION_SCALES } from '../constants/colorScales';

// ─── Helpers ──────────────────────────────────────────────────────────────────

const ALERT_DESCRIPTIONS = {
  NORMAL:     'La región/comuna se encuentra en condiciones de riesgo normal. No se requieren medidas preventivas inmediatas.',
  PREVENTIVO: 'La región/comuna presenta condiciones que requieren atención preventiva. Se recomienda monitoreo intensificado y coordinación con brigadas locales.',
  ALTO:       'La región/comuna presenta condiciones de riesgo alto. Se requieren medidas de preparación, alerta temprana y coordinación con autoridades.',
  CRITICO:    'La región/comuna presenta condiciones críticas de riesgo de incendio. Se requiere acción inmediata y activación de protocolos de emergencia.',
};

// Components can be plain numbers (WLC contribution, 0-maxWeight) or objects { score, weight, rawValue }.
function getCompScore(val) {
  if (typeof val === 'number') return val;
  if (val && typeof val.score === 'number') return val.score;
  return null;
}

function fmt(val, decimals = 1, suffix = '') {
  return val != null && isFinite(val) ? `${Number(val).toFixed(decimals)}${suffix}` : '—';
}

function vegetationLabel(value, indicator) {
  if (value == null || !isFinite(value)) return '—';
  const scale = VEGETATION_SCALES[indicator];
  const bin = scale.bins.find((b) => value < b.max);
  return bin ? bin.label : scale.bins[scale.bins.length - 1].label;
}

function fwiLabel(v) {
  if (v == null || !isFinite(v)) return '—';
  if (v < 11) return 'Bajo';
  if (v < 21) return 'Moderado';
  if (v < 38) return 'Alto';
  if (v < 50) return 'Muy alto';
  return 'Extremo';
}

function dateStr(isoString) {
  const d = isoString ? new Date(isoString) : new Date();
  return d.toLocaleString('es-CL', {
    timeZone: 'America/Santiago',
    day: '2-digit', month: 'long', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

function openReport(html) {
  const win = window.open('', '_blank');
  if (!win) return;
  win.document.write(html);
  win.document.close();
}

// ─── WLC weights (must match COMPONENT_META in ComunaRiskPanel) ────────────

const WLC_META = {
  STANDARD: [
    { key: 'fwi',     label: 'FWI meteorológico',         maxW: 0.52 },
    { key: 'firms',   label: 'Focos activos (FIRMS)',      maxW: 0.33 },
    { key: 'reports', label: 'Reportes ciudadanos',        maxW: 0.15 },
  ],
  ENHANCED: [
    { key: 'fwi',     label: 'FWI meteorológico',         maxW: 0.38 },
    { key: 'ndmi',    label: 'Humedad vegetación (NDMI)',  maxW: 0.22 },
    { key: 'firms',   label: 'Focos activos (FIRMS)',      maxW: 0.18 },
    { key: 'ndvi',    label: 'Índice vegetación (NDVI)',   maxW: 0.08 },
    { key: 'reports', label: 'Reportes ciudadanos',        maxW: 0.04 },
  ],
};

// ─── Shared CSS ───────────────────────────────────────────────────────────────

const SHARED_CSS = (alertColor, alertBg) => `
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: Arial, Helvetica, sans-serif; font-size: 13px; color: #111; margin: 36px 48px 80px; line-height: 1.5; }
  .print-bar { background: #1e293b; padding: 10px 24px; display: flex; align-items: center; justify-content: space-between; margin: -36px -48px 24px; }
  .print-bar span { color: #94a3b8; font-size: 11px; }
  .print-btn { background: #334155; color: #fff; border: none; border-radius: 6px; padding: 7px 16px; font-size: 12px; cursor: pointer; }
  .header { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 3px solid #111; padding-bottom: 12px; margin-bottom: 18px; }
  .header-left h1 { font-size: 16px; font-weight: 700; margin-bottom: 2px; }
  .header-left p { font-size: 10px; color: #555; }
  .header-right { text-align: right; font-size: 12px; min-width: 180px; }
  .header-right strong { display: block; font-size: 15px; font-weight: 700; }
  .header-right em { display: block; font-style: normal; font-size: 11px; color: #555; margin-bottom: 2px; }
  h2 { font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.06em; border-bottom: 1.5px solid #bbb; padding-bottom: 4px; margin: 18px 0 10px; color: #222; }
  .alert-box { border-left: 5px solid ${alertColor}; background: ${alertBg}; padding: 10px 14px; margin-bottom: 6px; border-radius: 0 6px 6px 0; }
  .alert-level { font-size: 14px; font-weight: 700; color: ${alertColor}; }
  .alert-badge { background: ${alertColor}; color: #fff; border-radius: 999px; padding: 1px 10px; font-size: 11px; font-weight: 700; }
  .alert-desc { font-size: 12px; color: #333; margin-top: 4px; }
  .alert-score { font-size: 11px; color: #555; margin-top: 6px; }
  table { width: 100%; border-collapse: collapse; margin-bottom: 4px; }
  thead th { background: #f0f0f0; padding: 6px 8px; text-align: left; font-size: 10px; text-transform: uppercase; letter-spacing: 0.04em; border-bottom: 1.5px solid #ccc; }
  tbody td { padding: 6px 8px; border-bottom: 1px solid #e8e8e8; font-size: 12px; vertical-align: middle; }
  tbody tr:last-child td { border-bottom: none; }
  .bar-wrap { height: 8px; border-radius: 4px; background: #e2e8f0; overflow: hidden; width: 90px; display: inline-block; vertical-align: middle; }
  .bar-fill { height: 100%; border-radius: 4px; background: ${alertColor}; }
  .footer { margin-top: 24px; padding-top: 10px; border-top: 1px solid #ccc; font-size: 10px; color: #666; }
  .footer p { margin-bottom: 3px; }
  .ref-table td { padding: 4px 8px; font-size: 11px; }
  .ref-table th { font-size: 10px; }
  .ref-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 4px; vertical-align: middle; }
  @media print { .print-bar { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }
`;

// ─── Thresholds section (shared) ─────────────────────────────────────────────

const THRESHOLDS_HTML = `
<h2>Umbrales de referencia de indicadores</h2>
<table class="ref-table">
  <thead><tr><th>Indicador</th><th>Rango</th><th>Interpretación</th></tr></thead>
  <tbody>
    <tr><td rowspan="5" style="font-weight:600;vertical-align:top;padding-top:6px;">FWI<br><span style="font-weight:400;font-size:10px;color:#555">Índice Peligro Incendio</span></td>
        <td>0 – 11</td><td><span class="ref-dot" style="background:#1e3a8a;"></span>Bajo</td></tr>
    <tr><td>11 – 21</td><td><span class="ref-dot" style="background:#78350f;"></span>Moderado</td></tr>
    <tr><td>21 – 38</td><td><span class="ref-dot" style="background:#7c2d12;"></span>Alto</td></tr>
    <tr><td>38 – 50</td><td><span class="ref-dot" style="background:#b45309;"></span>Muy alto</td></tr>
    <tr><td>&gt; 50</td><td><span class="ref-dot" style="background:#3f1d0b;"></span>Extremo</td></tr>
    <tr><td rowspan="4" style="font-weight:600;vertical-align:top;padding-top:6px;">NDVI<br><span style="font-weight:400;font-size:10px;color:#555">Índice vegetación (−1 a 1)</span></td>
        <td>&lt; 0</td><td>Superficie no vegetada (agua, suelo expuesto)</td></tr>
    <tr><td>0 – 0.2</td><td>Vegetación escasa o suelo desnudo</td></tr>
    <tr><td>0.2 – 0.5</td><td>Vegetación moderada</td></tr>
    <tr><td>&gt; 0.5</td><td>Vegetación densa y sana — menor riesgo</td></tr>
    <tr><td rowspan="3" style="font-weight:600;vertical-align:top;padding-top:6px;">NDMI<br><span style="font-weight:400;font-size:10px;color:#555">Humedad vegetación (−1 a 1)</span></td>
        <td>&gt; 0.1</td><td>Vegetación húmeda — bajo riesgo</td></tr>
    <tr><td>−0.1 a 0.1</td><td>Estrés hídrico leve</td></tr>
    <tr><td>&lt; −0.1</td><td>Estrés hídrico severo — alto riesgo</td></tr>
    <tr><td rowspan="4" style="font-weight:600;vertical-align:top;padding-top:6px;">Score WLC<br><span style="font-weight:400;font-size:10px;color:#555">Riesgo compuesto (0–100)</span></td>
        <td>0 – 20</td><td><span class="ref-dot" style="background:#1e3a8a;"></span>Normal</td></tr>
    <tr><td>20 – 40</td><td><span class="ref-dot" style="background:#78350f;"></span>Preventivo</td></tr>
    <tr><td>40 – 60</td><td><span class="ref-dot" style="background:#7c2d12;"></span>Alto</td></tr>
    <tr><td>&gt; 60</td><td><span class="ref-dot" style="background:#3f1d0b;"></span>Crítico</td></tr>
  </tbody>
</table>
<p style="font-size:10px;color:#666;margin-top:6px;">
  <strong>FIRMS</strong>: Focos activos detectados por satélite NASA en las últimas 24 h — a mayor número de focos de alta confianza, mayor riesgo inmediato.
  <strong>Reportes ciudadanos</strong>: observaciones verificadas de humo, focos o incendios activos que complementan los datos satelitales.
</p>`;

// ─── Informe Regional ─────────────────────────────────────────────────────────

export function generateRegionalReport({
  regionLabel, generatedAt, alertLevel, score,
  firms, alerts, reports,
  ndvi, ndmiVal, ndviLabel, ndmiLabel,
  wind, humidity, airTemp, soilTemp,
}) {
  const cfg = ALERT_LEVEL_CONFIG[alertLevel] || ALERT_LEVEL_CONFIG.NORMAL;
  const desc = ALERT_DESCRIPTIONS[alertLevel] || ALERT_DESCRIPTIONS.NORMAL;
  const date = dateStr(generatedAt);
  const reportsTotal = (reports.HUMO || 0) + (reports.FOCO || 0) + (reports.INCENDIO || 0) + (reports.OTRO || 0);

  const html = `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <title>Informe Regional SIMFAT — ${regionLabel} — ${date}</title>
  <style>${SHARED_CSS(cfg.color, cfg.bg)}</style>
</head>
<body>
  <div class="print-bar">
    <span>SIMFAT · Informe Regional · ${regionLabel}</span>
    <button class="print-btn" onclick="window.print()">Imprimir / Guardar PDF</button>
  </div>

  <div class="header">
    <div class="header-left">
      <h1>Informe Regional de Monitorización de Riesgo de Incendio</h1>
      <p>Sistema de Monitorización Forestal y Ambiental Territorial (SIMFAT) · AIFBN</p>
    </div>
    <div class="header-right">
      <strong>${regionLabel}</strong>
      <em>Generado: ${date}</em>
    </div>
  </div>

  <h2>Estado de Alerta Regional</h2>
  <div class="alert-box">
    <div class="alert-level">Nivel <span class="alert-badge">${cfg.label.toUpperCase()}</span></div>
    <div class="alert-desc">${desc}</div>
    <div class="alert-score">Score de riesgo compuesto (WLC): <strong>${score != null ? `${score}/100` : '—'}</strong></div>
  </div>

  <h2>Detecciones y Alertas</h2>
  <table>
    <thead><tr><th>Indicador</th><th>Total</th><th>Detalle</th></tr></thead>
    <tbody>
      <tr>
        <td>Focos activos (FIRMS / NASA)<br><span style="font-size:9px;color:#888;font-weight:400;">Últimos 7 días · vista regional bruta (sin atribución por comuna)</span></td>
        <td><strong>${firms.total}</strong></td>
        <td>Detectados hoy: ${firms.today} · Alta intensidad (FRP &gt; 50 MW): ${firms.highFrp}</td>
      </tr>
      <tr>
        <td>Alertas territoriales activas</td>
        <td><strong>${(alerts.PREVENTIVO || 0) + (alerts.ALTO || 0) + (alerts.CRITICO || 0)}</strong></td>
        <td>Preventivo: ${alerts.PREVENTIVO || 0} · Alto: ${alerts.ALTO || 0} · Crítico: ${alerts.CRITICO || 0}</td>
      </tr>
      <tr>
        <td>Reportes ciudadanos</td>
        <td><strong>${reportsTotal}</strong></td>
        <td>Humo: ${reports.HUMO || 0} · Foco: ${reports.FOCO || 0} · Incendio: ${reports.INCENDIO || 0} · Otro: ${reports.OTRO || 0}</td>
      </tr>
    </tbody>
  </table>

  <h2>Indicadores Ambientales</h2>
  <table>
    <thead><tr><th>Indicador</th><th>Valor (promedio regional)</th><th>Interpretación</th></tr></thead>
    <tbody>
      <tr><td>Índice de vegetación (NDVI)</td><td>${fmt(ndvi, 3)}</td><td>${ndviLabel || '—'}</td></tr>
      <tr><td>Humedad de vegetación (NDMI)</td><td>${fmt(ndmiVal, 3)}</td><td>${ndmiLabel || '—'}</td></tr>
      <tr><td>Velocidad del viento</td><td>${fmt(wind, 1, ' km/h')}</td><td>—</td></tr>
      <tr><td>Humedad relativa del aire</td><td>${fmt(humidity, 1, ' %')}</td><td>—</td></tr>
      <tr><td>Temperatura del aire</td><td>${fmt(airTemp, 1, ' °C')}</td><td>—</td></tr>
      <tr><td>Temperatura del suelo</td><td>${fmt(soilTemp, 1, ' °C')}</td><td>—</td></tr>
    </tbody>
  </table>

  ${THRESHOLDS_HTML}

  <div class="footer">
    <p><strong>Fuentes de datos:</strong> NASA FIRMS (detecciones térmicas satelitales) · Copernicus CDSE / OpenEO (NDVI, NDMI) · Open-Meteo (variables climáticas) · SIMFAT reportes ciudadanos verificados.</p>
    <p>Este informe fue generado automáticamente por el Sistema SIMFAT y no reemplaza la evaluación técnica de un profesional en terreno. Para más información contacte a AIFBN.</p>
  </div>
</body>
</html>`;

  openReport(html);
}

// ─── Informe Comunal ──────────────────────────────────────────────────────────

export function generateComunalReport({ score, comunaId, regionLabel, generatedAt }) {
  if (!score) return;

  const alertLevel = score.alertLevel || 'NORMAL';
  const cfg = ALERT_LEVEL_CONFIG[alertLevel] || ALERT_LEVEL_CONFIG.NORMAL;
  const desc = ALERT_DESCRIPTIONS[alertLevel] || ALERT_DESCRIPTIONS.NORMAL;
  const date = dateStr(generatedAt || score.computedAt);

  const compositeScore = typeof score.scoreComposite === 'number'
    ? Math.round(score.scoreComposite * 100) : null;

  const nombreComuna = score.nombreComuna || comunaId || score.comunaId || 'Comuna';
  const comps = score.components || {};
  const mode = score.mode || 'STANDARD';
  const modeLabel = mode === 'ENHANCED' ? 'ENHANCED (Copernicus)' : 'STANDARD';

  // Raw indicator values from top-level fields (set by backend after WLC computation)
  const ndvi = score.ndviRaw ?? null;
  const ndmi = score.ndmiRaw ?? null;
  const fwiRaw = score.fwiRaw ?? null;
  const firmsCount = score.firmsCount ?? null;
  const firmsFrpMean = score.firmsFrpMean ?? null;
  const reportsCount = score.reportsCount ?? null;

  const meta = WLC_META[mode] || WLC_META.STANDARD;

  const wlcTableRows = meta.map((r) => {
    const rawScore = getCompScore(comps[r.key]);
    const pts = rawScore != null ? Math.round(rawScore * 100) : null;
    const fillPct = rawScore != null && r.maxW > 0
      ? Math.min(Math.round((rawScore / r.maxW) * 100), 100) : 0;

    let observedValue = '—';
    if (r.key === 'ndvi' && ndvi != null) observedValue = fmt(ndvi, 3);
    else if (r.key === 'ndmi' && ndmi != null) observedValue = fmt(ndmi, 3);
    else if (r.key === 'fwi' && fwiRaw != null) observedValue = `${fmt(fwiRaw, 1)} (${fwiLabel(fwiRaw)})`;
    else if (r.key === 'firms' && firmsCount != null) {
      observedValue = `${firmsCount} foco${firmsCount === 1 ? '' : 's'} <span style="font-size:9px;color:#888;">(últimas 48h, por comuna)</span>`;
      if (firmsFrpMean) observedValue += ` (FRP ${fmt(firmsFrpMean, 1)} MW)`;
    }
    else if (r.key === 'reports' && reportsCount != null) observedValue = `${reportsCount} reporte${reportsCount === 1 ? '' : 's'}`;

    const barHtml = `<div class="bar-wrap"><div class="bar-fill" style="width:${fillPct}%"></div></div>`;
    return `<tr>
      <td>${r.label}</td>
      <td style="text-align:right;">${(r.maxW * 100).toFixed(0)}%</td>
      <td>${observedValue}</td>
      <td>${pts != null ? `<strong>${pts}/100</strong>` : '—'} &nbsp;${barHtml}</td>
    </tr>`;
  }).join('');

  const html = `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <title>Informe Comunal SIMFAT — ${nombreComuna} — ${date}</title>
  <style>
    ${SHARED_CSS(cfg.color, cfg.bg)}
    .mode-badge { display: inline-block; background: #f0fdf4; border: 1px solid #86efac; color: #166534; border-radius: 4px; padding: 1px 8px; font-size: 10px; font-weight: 700; margin-left: 6px; }
  </style>
</head>
<body>
  <div class="print-bar">
    <span>SIMFAT · Informe Comunal · ${nombreComuna}, ${regionLabel}</span>
    <button class="print-btn" onclick="window.print()">Imprimir / Guardar PDF</button>
  </div>

  <div class="header">
    <div class="header-left">
      <h1>Informe Comunal de Monitorización de Riesgo de Incendio</h1>
      <p>Sistema de Monitorización Forestal y Ambiental Territorial (SIMFAT) · AIFBN</p>
    </div>
    <div class="header-right">
      <strong>${nombreComuna}</strong>
      <em>${regionLabel}</em>
      <em>Generado: ${date}</em>
    </div>
  </div>

  <h2>Estado de Alerta Comunal</h2>
  <div class="alert-box">
    <div class="alert-level">
      Nivel <span class="alert-badge">${cfg.label.toUpperCase()}</span>
      <span class="mode-badge">${modeLabel}</span>
    </div>
    <div class="alert-desc">${desc}</div>
    <div class="alert-score">Score de riesgo compuesto (WLC): <strong>${compositeScore != null ? `${compositeScore}/100` : '—'}</strong></div>
  </div>

  <h2>Desglose por componente WLC</h2>
  <table>
    <thead><tr><th>Componente</th><th>Peso máx.</th><th>Valor observado</th><th>Contribución al score</th></tr></thead>
    <tbody>${wlcTableRows}</tbody>
  </table>
  <p style="font-size:10px;color:#666;margin-top:4px;">
    La barra indica qué proporción del peso máximo de ese componente se activó. El score es la contribución efectiva al riesgo compuesto total (máx. 100 pts).
  </p>

  <h2>Indicadores de Vegetación (Copernicus)</h2>
  <table>
    <thead><tr><th>Indicador</th><th>Valor</th><th>Interpretación</th></tr></thead>
    <tbody>
      <tr><td>Índice de vegetación (NDVI)</td><td>${fmt(ndvi, 3)}</td><td>${vegetationLabel(ndvi, 'NDVI')}</td></tr>
      <tr><td>Humedad de vegetación (NDMI)</td><td>${fmt(ndmi, 3)}</td><td>${vegetationLabel(ndmi, 'NDMI')}</td></tr>
    </tbody>
  </table>

  ${THRESHOLDS_HTML}

  <div class="footer">
    <p><strong>Fuentes de datos:</strong> NASA FIRMS (detecciones térmicas) · Copernicus CDSE / OpenEO (NDVI, NDMI) · Open-Meteo (FWI) · SIMFAT reportes ciudadanos verificados.</p>
    <p>Las variables climáticas (viento, humedad, temperatura) están disponibles en el informe regional. Este informe no reemplaza la evaluación técnica de un profesional en terreno. Para más información contacte a AIFBN.</p>
  </div>
</body>
</html>`;

  openReport(html);
}
