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
// observation per comuna (TerritoryController), hence the "Última observación"
// subtitle. Order here is the render order.
export const LAYER_TOGGLE_GROUPS = [
  {
    id: 'events',
    title: 'Riesgo y eventos',
    subtitle: 'Situación actual',
    note: 'El riesgo es el valor actual; no depende del año elegido en las capas MapBiomas.',
    indicators: ['RISK_SCORE', 'FIRMS', 'ALERTS', 'REPORTS']
  },
  {
    id: 'mapbiomas',
    title: 'MapBiomas',
    subtitle: 'Historia anual (elige el año bajo el mapa)',
    indicators: [...MAPBIOMAS_INDICATORS]
  },
  {
    id: 'conditions',
    title: 'Condiciones actuales',
    subtitle: 'Última observación satelital y climática',
    collapsible: true,
    indicators: ['NDVI', 'NDMI', ...CLIMATE_INDICATORS]
  }
];

// Collapsible groups are open on wide screens and whenever one of their layers
// is visible; on narrow screens they start closed. An explicit user choice
// (override true/false) wins over that default.
export function isToggleGroupExpanded({ group, visibleIndicators = [], narrow = false, override = null }) {
  if (!group?.collapsible) return true;
  if (typeof override === 'boolean') return override;
  return !narrow || group.indicators.some((indicator) => visibleIndicators.includes(indicator));
}
