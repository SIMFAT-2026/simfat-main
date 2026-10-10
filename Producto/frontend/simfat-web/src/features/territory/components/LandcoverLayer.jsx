import { memo, useRef } from 'react';
import { GeoJSON } from 'react-leaflet';
import { NEUTRAL_FILL } from '../constants/colorScales';
import { landcoverColorFor } from '../constants/landcoverClasses';
import { landcoverLegendClasses, landcoverLegendNote, landcoverTooltipLines } from '../utils/landcoverLayer';

// Leaflet renders string tooltips as HTML, so build a DOM node with textContent:
// comuna names and labels can never be interpreted as markup.
function tooltipElement(lines) {
  const root = document.createElement('div');
  lines.forEach((line, index) => {
    const row = document.createElement('div');
    row.textContent = line;
    if (index === 0) row.style.fontWeight = '600';
    root.appendChild(row);
  });
  return root;
}

// Yearly MapBiomas LANDCOVER layer: each comuna is filled with the official
// colour of its dominant class. Keyed on the layer year so another year rebuilds
// and restyles the polygons. While the layer is not loaded every comuna is
// neutral and the tooltip says why. When `interactive` is false (a layer above
// it, RISK_SCORE or BURNED, owns hover and click) the polygons take no pointer
// events. `emphasizeOutline` thickens the neutral outline while the layer is the
// only thing on the map and not loaded.
export const LandcoverChoropleth = memo(function LandcoverChoropleth({
  geoJson,
  layer,
  loading,
  interactive = true,
  emphasizeOutline = false
}) {
  const layerRef = useRef(layer);
  layerRef.current = layer;
  const loadingRef = useRef(loading);
  loadingRef.current = loading;

  if (!geoJson || !geoJson.features) return null;

  return (
    <GeoJSON
      key={`landcover-${layer?.year ?? 'none'}-${interactive ? 'i' : 'n'}-${emphasizeOutline ? 'e' : 'p'}`}
      pane="landcover-layer-pane"
      interactive={interactive}
      data={geoJson}
      style={(feature) => {
        const comunaId = feature?.properties?.comunaId;
        const entry = layerRef.current?.values?.[comunaId];
        return {
          fillColor: landcoverColorFor(entry),
          fillOpacity: entry?.available === true ? 0.6 : 0.12,
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
            layerGeo.setTooltipContent(
              tooltipElement([nombre, loadingRef.current ? 'Cargando…' : 'Sin datos de cobertura'])
            );
            return;
          }
          layerGeo.setTooltipContent(
            tooltipElement(landcoverTooltipLines({ nombre, year: current.year, entry: current.values?.[comunaId] }))
          );
        });
      }}
    />
  );
});

// Legend: year, only the classes present on the map, a factual note and the
// source. Names and counts are plain text so colour is never the only channel.
export function LandcoverLegend({ state }) {
  const layer = state?.layer;
  const title = layer ? `Cobertura del suelo ${layer.year}` : 'Cobertura del suelo';
  const note = landcoverLegendNote(layer, { hasYearControl: true });
  const classes = landcoverLegendClasses(layer);
  const failed = !layer && state?.error && !state?.loading;

  return (
    <div className="territory-legend-section">
      <h5>{title}</h5>
      {failed && (
        <div className="territory-meta" role="alert">
          <p>{state.error}</p>
          {state.retry && (
            <button type="button" className="btn btn-secondary" onClick={state.retry}>
              Reintentar
            </button>
          )}
        </div>
      )}
      {!layer && !failed && (
        <p className="territory-meta" role="status">
          {state?.loading ? 'Cargando…' : 'Sin datos de cobertura.'}
        </p>
      )}
      {layer && (
        <>
          <ul>
            {classes.map((item) => (
              <li key={item.code}>
                <span
                  className="territory-color-dot"
                  style={{ backgroundColor: item.color, border: '1px solid #94a3b8' }}
                />
                <span>{item.label}</span>
                <strong>{item.count}</strong>
              </li>
            ))}
            <li>
              <span className="territory-color-dot" style={{ backgroundColor: NEUTRAL_FILL }} />
              <span>Sin datos</span>
            </li>
          </ul>
          <p className="territory-meta">Cada comuna se colorea según su clase de cobertura dominante.</p>
          {note && <p className="territory-meta">{note}</p>}
          <p className="territory-meta">
            Fuente: {layer.citation || 'MapBiomas Chile, Colección 2 (CC BY 4.0)'}. Colores: leyenda oficial de
            MapBiomas Chile.
          </p>
        </>
      )}
    </div>
  );
}
