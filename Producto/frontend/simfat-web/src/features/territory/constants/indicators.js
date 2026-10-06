// Single source of truth for the indicators served as comuna-keyed value maps
// ({ indicator, unit, values: { comunaId: {...} } }) instead of GeoJSON.

// Opt-in climate layers rendered as comuna choropleths (see ClimateChoropleth).
export const CLIMATE_INDICATORS = ['WIND', 'HUMIDITY', 'AIR_TEMP', 'SOIL_TEMP'];

// MapBiomas layers: yearly, fetched on demand (never in the region bulk load).
export const MAPBIOMAS_INDICATORS = ['BURNED', 'LANDCOVER'];

// Everything the normaliser must keep as a value map.
export const VALUE_MAP_INDICATORS = [...CLIMATE_INDICATORS, ...MAPBIOMAS_INDICATORS];
