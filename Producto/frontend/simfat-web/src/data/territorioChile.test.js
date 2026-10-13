import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { comunaDisplayName, hasCuratedName } from './territorioChile';

const GEOJSON_DIR = new URL(
  '../../../../backend/simfat-backend/src/main/resources/static/geojson/',
  import.meta.url
);
const FILES = ['comunas-biobio', 'comunas-nuble', 'comunas-araucania'];

// Same folding the analytics pipeline uses: GADM names drop spaces and the
// canonical names carry them, so compare without spaces/case/accents.
const fold = (s) =>
  s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/\s+/g, '').toLowerCase();

describe('comunaDisplayName', () => {
  it('restores spaces and accents for GADM CamelCase names', () => {
    expect(comunaDisplayName('CHL.6.2.5_1', 'LosAngeles')).toBe('Los Ángeles');
    expect(comunaDisplayName('CHL.6.3.9_1', 'SanPedrodelaPaz')).toBe('San Pedro de la Paz');
  });

  it('falls back to the raw name, then the id, for unknown comunas', () => {
    expect(comunaDisplayName('CHL.9.9.9_1', 'Foo')).toBe('Foo');
    expect(comunaDisplayName('CHL.9.9.9_1')).toBe('CHL.9.9.9_1');
  });

  it('covers every boundary feature and only differs from GADM by spacing/accents', () => {
    let total = 0;
    for (const file of FILES) {
      const geo = JSON.parse(readFileSync(new URL(`${file}.geojson`, GEOJSON_DIR), 'utf8'));
      for (const f of geo.features) {
        total += 1;
        expect(hasCuratedName(f.properties.comunaId), `${f.properties.comunaId} missing curated name`).toBe(true);
        const { comunaId, nombre } = f.properties;
        const label = comunaDisplayName(comunaId, nombre);
        expect(fold(label), comunaId).toBe(fold(nombre));
      }
    }
    expect(total).toBe(86);
  });

  it('never leaves a CamelCase join in a label', () => {
    for (const file of FILES) {
      const geo = JSON.parse(readFileSync(new URL(`${file}.geojson`, GEOJSON_DIR), 'utf8'));
      for (const f of geo.features) {
        const label = comunaDisplayName(f.properties.comunaId, f.properties.nombre);
        expect(label, f.properties.comunaId).not.toMatch(/[a-záéíóúñ][A-ZÁÉÍÓÚÑ]/);
      }
    }
  });
});
