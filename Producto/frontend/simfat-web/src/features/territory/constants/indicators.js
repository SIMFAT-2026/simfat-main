// Single source of truth for the indicators served as comuna-keyed value maps
// ({ indicator, unit, values: { comunaId: {...} } }) instead of GeoJSON.

// Opt-in climate layers rendered as comuna choropleths (see ClimateChoropleth).
export const CLIMATE_INDICATORS = ['WIND', 'HUMIDITY', 'AIR_TEMP', 'SOIL_TEMP'];

// MapBiomas layers: yearly, fetched on demand (never in the region bulk load).
export const MAPBIOMAS_INDICATORS = ['BURNED', 'LANDCOVER'];

// Everything the normaliser must keep as a value map.
export const VALUE_MAP_INDICATORS = [...CLIMATE_INDICATORS, ...MAPBIOMAS_INDICATORS];

// Critical: needed for the first meaningful paint (choropleth + active fires + alerts).
// Secondary: useful context loaded after the map is already interactive.
// MapBiomas layers are deliberately NOT in either bulk-load group: they are
// fetched on demand per year (fetchMapbiomasLayer), which also keeps
// DashboardPage from double-fetching them.
export const CRITICAL_INDICATORS = ['RISK_SCORE', 'ALERTS', 'FIRMS'];
export const SECONDARY_INDICATORS = ['NDVI', 'NDMI', 'REPORTS', ...CLIMATE_INDICATORS];

// Every indicator accepted as an initial visible layer (deep links, ?focus=).
export const DEFAULT_INDICATORS = [...CRITICAL_INDICATORS, ...SECONDARY_INDICATORS, ...MAPBIOMAS_INDICATORS];

// WIND/HUMIDITY/AIR_TEMP/SOIL_TEMP are opt-in climate layers: fetched alongside
// the rest but not shown until the user toggles them on (DEC-B).
export const DEFAULT_VISIBLE_INDICATORS = ['RISK_SCORE', 'FIRMS', 'ALERTS', 'REPORTS'];

// Layer toggles grouped by time semantics (the question users ask is "is this
// today or history?"): current events, yearly MapBiomas history, and the latest
// satellite/weather observations. NDVI/NDMI come from the latest OpenEO
// composites and the climate layers from the latest persisted weather
// observation per comuna (TerritoryController). Order here is the render order.
// `placement` decides where the chips render: always visible ('inline') or
// behind the "Más capas" popover ('popover'). `info` feeds the info button.
export const LAYER_TOGGLE_GROUPS = [
  {
    id: 'events',
    title: 'Riesgo y eventos',
    placement: 'inline',
    info: 'Riesgo y eventos: situación actual. El riesgo es el valor actual; no depende del año elegido en las capas MapBiomas.',
    indicators: ['RISK_SCORE', 'FIRMS', 'ALERTS', 'REPORTS']
  },
  {
    id: 'mapbiomas',
    title: 'MapBiomas',
    placement: 'inline',
    info: 'MapBiomas: historia anual (elige el año junto a cada capa).',
    indicators: [...MAPBIOMAS_INDICATORS]
  },
  {
    id: 'conditions',
    title: 'Condiciones actuales',
    context: 'Última observación satelital y climática',
    placement: 'popover',
    info: 'Más capas: última observación satelital y climática.',
    indicators: ['NDVI', 'NDMI', ...CLIMATE_INDICATORS]
  }
];

export function layerGroupInfoLines() {
  return LAYER_TOGGLE_GROUPS.map((group) => group.info);
}

// "(2 activas)" for the group's visible layers, else ''.
export function activeLayerSummary({ group, visibleIndicators = [] }) {
  const count = group.indicators.filter((indicator) => visibleIndicators.includes(indicator)).length;
  if (count === 0) return '';
  return `(${count} ${count === 1 ? 'activa' : 'activas'})`;
}

