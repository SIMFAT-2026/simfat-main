import { useEffect, useRef } from 'react';
import { useComunaMapbiomas } from '../hooks/useComunaMapbiomas';
import {
  comunaMapbiomasErrorView,
  coverageView,
  fireView,
  indicesRows,
  scoreSentences
} from '../utils/comunaMapbiomas';

function CoverageBlock({ landCover }) {
  const view = coverageView(landCover);
  if (view.state === 'empty') {
    return <p className="mb-note">{view.message}</p>;
  }
  const rows = [...view.bars];
  if (view.others) rows.push({ code: 'others', label: 'Otras', color: '#cbd5e1', share: view.others.share, text: view.others.text });
  return (
    <div>
      <h6 className="mb-subtitle">{view.title}</h6>
      <ul className="mb-bars">
        {rows.map((row) => (
          <li key={row.code} className="mb-bar-row">
            <span className="mb-bar-label" title={row.label}>{row.label}</span>
            <span className="mb-bar-track" aria-hidden="true">
              <span
                className="mb-bar-fill"
                style={{ width: `${Math.max(row.share * 100, 1)}%`, backgroundColor: row.color }}
              />
            </span>
            <span className="mb-bar-value">{row.text}</span>
          </li>
        ))}
      </ul>
      <p className="mb-source">Porcentajes redondeados.</p>
      {view.unobservedText && <p className="mb-note">{view.unobservedText}</p>}
    </div>
  );
}

function FireBlock({ data, highlightYear }) {
  const view = fireView(data.fire, data.fireHistoryAvailable, highlightYear);
  if (view.state === 'empty') {
    return <p className="mb-note">{view.message}</p>;
  }
  return (
    <div>
      <h6 className="mb-subtitle">{view.title}</h6>
      <div className="mb-fire-chart" role="img" aria-label={`${view.title}, hectáreas quemadas por año: ${view.summary}`}>
        {view.bars.map((bar) => (
          <span key={bar.year} className="mb-fire-col">
            <span
              className={`mb-fire-bar${bar.highlighted ? ' is-highlighted' : ''}`}
              style={{ height: `${bar.heightPct}%` }}
            />
          </span>
        ))}
      </div>
      <div className="mb-fire-axis" aria-hidden="true">
        <span>{view.firstYearLabel}</span>
        <span>{view.lastYearLabel}</span>
      </div>
      {view.selectedText && <p className="mb-note">{view.selectedText}</p>}
      <p className="mb-note">{view.cumulativeText}</p>
      <p className="mb-note">{view.lastFireText}</p>
    </div>
  );
}

function Attribution({ attribution }) {
  if (!attribution?.text) return null;
  const license =
    attribution.license && !attribution.text.includes(attribution.license) ? ` (${attribution.license})` : '';
  return (
    <p className="mb-source">
      {attribution.text}
      {license}
      {attribution.sourceUrl && (
        <>
          {' '}
          <a href={attribution.sourceUrl} target="_blank" rel="noopener noreferrer">Fuente</a>
        </>
      )}
    </p>
  );
}

// "MapBiomas" section of the comuna panel: land cover, fire history, indices and
// an honest note on how (or whether) MapBiomas feeds the risk score.
export default function ComunaMapbiomasSection({ comunaId, year, burnedYear }) {
  const { loading, stale, data, error, retry } = useComunaMapbiomas(comunaId, year);
  const errorView = error ? comunaMapbiomasErrorView(error) : null;
  const headingRef = useRef(null);
  const alertRef = useRef(null);
  const retryPendingRef = useRef(false);

  // After "Reintentar" the button unmounts: park focus on the heading while the
  // retry runs, then on the alert (new retry button lives inside it) if it fails.
  useEffect(() => {
    if (!retryPendingRef.current || loading) return;
    retryPendingRef.current = false;
    if (error) alertRef.current?.focus();
  }, [loading, error]);

  function handleRetry() {
    retryPendingRef.current = true;
    headingRef.current?.focus();
    retry();
  }

  return (
    <section className="panel-section" aria-labelledby="mb-section-title" aria-busy={loading}>
      <h5 className="panel-section-title" id="mb-section-title" tabIndex={-1} ref={headingRef}>MapBiomas</h5>
      <div role="status">
        {/* While refreshing over stale data the text is screen-reader only, so
            no line is inserted above the bars (that shift read as a flicker). */}
        {loading && (
          <p className={stale ? 'sr-only' : 'mb-note'}>
            {stale ? 'Actualizando datos MapBiomas…' : 'Cargando datos MapBiomas…'}
          </p>
        )}
      </div>
      {errorView && (
        <div role="alert" tabIndex={-1} ref={alertRef}>
          <p className="mb-note">{errorView.message}</p>
          {errorView.canRetry && (
            <button type="button" className="btn btn-secondary btn-sm mb-retry" onClick={handleRetry}>
              Reintentar
            </button>
          )}
        </div>
      )}
      {data && (
        <div className={stale ? 'mb-content mb-content-stale' : 'mb-content'}>
          <CoverageBlock landCover={data.landCover} />
          <FireBlock data={data} highlightYear={burnedYear} />
          <div>
            <h6 className="mb-subtitle">Índices MapBiomas</h6>
            <dl className="mb-indices">
              {indicesRows(data.indices).map((row) => (
                <div key={row.label} className="mb-index-row">
                  <dt>{row.label}</dt>
                  <dd>{row.value}</dd>
                </div>
              ))}
            </dl>
            {scoreSentences(data.indices).map((sentence) => (
              <p key={sentence} className="mb-note">{sentence}</p>
            ))}
          </div>
          <Attribution attribution={data.attribution} />
        </div>
      )}
    </section>
  );
}
