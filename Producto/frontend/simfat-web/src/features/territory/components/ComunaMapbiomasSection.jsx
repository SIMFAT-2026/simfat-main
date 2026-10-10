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
      <ul className="mb-bars" aria-label={`${view.title}: ${view.summary}`}>
        {rows.map((row) => (
          <li key={row.code} className="mb-bar-row">
            <span className="mb-bar-label">{row.label}</span>
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
          <span key={bar.year} className="mb-fire-col" title={bar.label}>
            <span
              className={`mb-fire-bar${bar.highlighted ? ' is-highlighted' : ''}`}
              style={{ height: `${bar.heightPct}%` }}
            />
          </span>
        ))}
      </div>
      <p className="mb-note">{view.cumulativeText}</p>
      <p className="mb-note">{view.lastFireText}</p>
    </div>
  );
}

// "MapBiomas" section of the comuna panel: land cover, fire history, indices and
// an honest note on how (or whether) MapBiomas feeds the risk score.
export default function ComunaMapbiomasSection({ comunaId, year, burnedYear }) {
  const { loading, data, error, retry } = useComunaMapbiomas(comunaId, year);
  const errorView = error ? comunaMapbiomasErrorView(error) : null;

  return (
    <section className="panel-section" aria-labelledby="mb-section-title" aria-busy={loading}>
      <h5 className="panel-section-title" id="mb-section-title">MapBiomas</h5>
      {loading && <p className="mb-note" role="status">Cargando datos MapBiomas…</p>}
      {errorView && (
        <div role="alert">
          <p className="mb-note">{errorView.message}</p>
          {errorView.canRetry && (
            <button type="button" className="btn btn-secondary btn-sm mb-retry" onClick={retry}>
              Reintentar
            </button>
          )}
        </div>
      )}
      {data && (
        <>
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
          {data.attribution?.text && (
            <p className="mb-source">
              {data.attribution.text}
              {data.attribution.license && !data.attribution.text.includes(data.attribution.license)
                ? ` (${data.attribution.license})`
                : ''}
            </p>
          )}
        </>
      )}
    </section>
  );
}
