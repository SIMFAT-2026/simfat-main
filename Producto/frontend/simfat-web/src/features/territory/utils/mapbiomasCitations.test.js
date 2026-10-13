import { describe, expect, it } from 'vitest';
import {
  MAPBIOMAS_FIRE_CITATION,
  MAPBIOMAS_LANDCOVER_CITATION,
} from './mapbiomasCitations';

describe('MapBiomas fallback citations (terms-of-use format)', () => {
  it('landcover fallback follows the official template', () => {
    expect(MAPBIOMAS_LANDCOVER_CITATION).toBe(
      'MapBiomas – Colección 2 de la Serie Anual de Mapas de Cobertura y Uso del Suelo de Chile, consultada el 21 de septiembre de 2026 a través del enlace: https://chile.mapbiomas.org/wp-content/uploads/sites/7/2026/08/statistics_lulc_chile_col2_political_level_234.xlsx',
    );
  });

  it('fire fallback follows the official template', () => {
    expect(MAPBIOMAS_FIRE_CITATION).toBe(
      'MapBiomas – Colección 1 de la Serie Anual de Mapas de Área Quemada de Chile (Fuego, 2013–2025), consultada el 30 de septiembre de 2026 a través del enlace: https://storage.googleapis.com/mapbiomas-public/initiatives/chile/fire/collection1/',
    );
  });
});
