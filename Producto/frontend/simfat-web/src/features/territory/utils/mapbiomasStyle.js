// Pure fill-opacity decisions for the MapBiomas choropleths.

const NO_DATA_OPACITY = 0.12;
const BURNED_OPACITY = 0.55;
const LANDCOVER_OPACITY = 0.6;
// LANDCOVER (pane 412) paints above RISK_SCORE (410): when both are visible the
// cover fill is lowered so the risk colours read through it.
const LANDCOVER_OVER_RISK_OPACITY = 0.35;
// A stale layer (previous year, kept while the new year loads) is dimmed.
const STALE_FACTOR = 0.5;

function withStale(opacity, stale) {
  return stale ? opacity * STALE_FACTOR : opacity;
}

export function landcoverFillOpacity({ hasData, riskVisible, stale = false }) {
  if (!hasData) return NO_DATA_OPACITY;
  return withStale(riskVisible ? LANDCOVER_OVER_RISK_OPACITY : LANDCOVER_OPACITY, stale);
}

export function burnedFillOpacity({ hasData, stale = false }) {
  if (!hasData) return NO_DATA_OPACITY;
  return withStale(BURNED_OPACITY, stale);
}
