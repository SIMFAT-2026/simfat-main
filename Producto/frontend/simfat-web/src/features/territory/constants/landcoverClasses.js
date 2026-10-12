import { NEUTRAL_FILL } from './colorScales';

// Official MapBiomas Chile Collection 2 land-cover classes: code, Spanish name
// and colour, from the official legend (Leyenda2025-LEYENDA-CODIGOS.pdf, sha256
// c812298d...). Class 27 "No observado" is white, so legend dots carry an outline.
export const LANDCOVER_CLASSES = {
  59: { label: 'Bosque Primario', color: '#1F8D49' },
  60: { label: 'Bosque Secundario', color: '#5CB85D' },
  67: { label: 'Bosque Achaparrado', color: '#C8FFB4' },
  11: { label: 'Humedal', color: '#519799' },
  12: { label: 'Pastizal', color: '#D6BC74' },
  63: { label: 'Estepa', color: '#C7E0AB' },
  66: { label: 'Matorral', color: '#A89358' },
  29: { label: 'Afloramiento Rocoso', color: '#FFAA5F' },
  9: { label: 'Silvicultura', color: '#7A5900' },
  18: { label: 'Agricultura', color: '#E974ED' },
  15: { label: 'Pastura', color: '#EDDE8E' },
  24: { label: 'Infraestructura', color: '#D4271E' },
  23: { label: 'Arena, Playa y Duna', color: '#FFA07A' },
  61: { label: 'Salar', color: '#F5D5D5' },
  25: { label: 'Otra área sin vegetación', color: '#DB4D4F' },
  33: { label: 'Río, lago u océano', color: '#2532E4' },
  34: { label: 'Hielo y nieve', color: '#93DFE6' },
  27: { label: 'No observado', color: '#FFFFFF' }
};

// entry: { available, dominantClass } from the LANDCOVER value map. No data or a
// class outside the official palette reads as no data, never as a made-up colour.
export function landcoverColorFor(entry) {
  if (entry?.available !== true) {
    return NEUTRAL_FILL;
  }
  return LANDCOVER_CLASSES[entry.dominantClass]?.color ?? NEUTRAL_FILL;
}
