// Pure fill-opacity decisions for the MapBiomas choropleths.

const NO_DATA_OPACITY = 0.12;
const LANDCOVER_OPACITY = 0.6;
// LANDCOVER (pane 412) paints above RISK_SCORE (410): when both are visible the
// cover fill is lowered so the risk colours read through it.
const LANDCOVER_OVER_RISK_OPACITY = 0.35;

export function landcoverFillOpacity({ hasData, riskVisible }) {
  if (!hasData) return NO_DATA_OPACITY;
  return riskVisible ? LANDCOVER_OVER_RISK_OPACITY : LANDCOVER_OPACITY;
}
