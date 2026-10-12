import { useId, useState } from 'react';
import { ALERT_LEVEL_CONFIG } from '../constants/colorScales';
import { ALERT_RULE_EXPLANATION } from '../utils/alertTrigger';
import { useDisclosure } from './useDisclosure';

// One-line regional summary plus a "Ver detalle" disclosure (level
// distribution and top comunas). `summary` comes from regionalSummary().
function RegionalSummary({ regionLabel, summary, onComunaClick }) {
  const [open, setOpen] = useState(false);
  const detailId = useId();
  const infoId = useId();
  const info = useDisclosure();
  const { comunaCount, comunasText, alertText, max, maxTies, top, distribution, firmsCount48h, reports7d } = summary;

  return (
    <section className="regional-summary-panel" aria-labelledby={`${detailId}-title`}>
      <div className="regional-summary-line">
        <h4 id={`${detailId}-title`} className="regional-summary-title">Resumen regional</h4>
        <span className="regional-summary-text">
          <strong>{regionLabel}</strong>
          {comunasText ? ` · ${comunasText}` : ''}
          {` · ${alertText}`}
          {max ? ` · Puntaje máx.: ${max.name} ${max.pct}/100${maxTies > 0 ? ` (+${maxTies} con igual puntaje)` : ''}` : ''}
          {firmsCount48h !== null ? ` · Focos FIRMS 48 h: ${firmsCount48h}` : ''}
          {reports7d !== null ? ` · Reportes 7 d: ${reports7d}` : ''}
        </span>
        <span className="territory-disclosure">
          <button
            ref={info.buttonRef}
            type="button"
            className="territory-info-button"
            aria-label="Cómo se define el nivel de alerta"
            aria-expanded={info.open}
            aria-controls={infoId}
            onClick={info.toggle}
          >
            <span aria-hidden="true">ⓘ</span>
          </button>
          <div ref={info.panelRef} id={infoId} className="territory-popover territory-info-panel" hidden={!info.open}>
            <p>{ALERT_RULE_EXPLANATION}</p>
          </div>
        </span>
        {comunaCount > 0 && (
          <button
            type="button"
            className="regional-summary-toggle"
            aria-expanded={open}
            aria-controls={detailId}
            onClick={() => setOpen((value) => !value)}
          >
            Ver detalle <span aria-hidden="true">{open ? '▴' : '▾'}</span>
          </button>
        )}
      </div>
      <div id={detailId} className="regional-summary-detail" hidden={!open}>
        <ul className="regional-summary-levels" aria-label="Comunas por nivel de alerta">
          {distribution.map(({ level, label, count }) => (
            <li key={level} style={{ color: ALERT_LEVEL_CONFIG[level].color }}>
              {label}: <strong>{count}</strong>
            </li>
          ))}
        </ul>
        <ol className="regional-summary-top" aria-label="Comunas con mayor puntaje">
          {top.map(({ comunaId, name, pct, level, label }) => (
            <li key={comunaId}>
              <button type="button" className="regional-summary-comuna" onClick={() => onComunaClick?.(comunaId, name)}>
                {name}
              </button>
              <span>{` ${pct}/100 · `}</span>
              <span style={{ color: ALERT_LEVEL_CONFIG[level].color }}>{label}</span>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

export default RegionalSummary;
