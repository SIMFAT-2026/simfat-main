import { describe, expect, it } from 'vitest';
import { LANDCOVER_CLASSES, landcoverColorFor } from '../constants/landcoverClasses';
import { NEUTRAL_FILL } from '../constants/colorScales';
import {
  landcoverLegendClasses,
  landcoverLegendNote,
  landcoverLineFor,
  landcoverToggleLabel,
  landcoverTooltipLines
} from './landcoverLayer';

function entry(code, label, share, top = []) {
  return { available: true, dominantClass: code, dominantLabel: label, dominantShare: share, top, unobservedShare: 0 };
}

const NO_DATA = {
  available: false,
  dominantClass: null,
  dominantLabel: null,
  dominantShare: null,
  top: [],
  unobservedShare: null
};

describe('landcover palette', () => {
  it('uses the official MapBiomas Chile Col 2 colours', () => {
    expect(LANDCOVER_CLASSES[9]).toEqual({ label: 'Silvicultura', color: '#7A5900' });
    expect(LANDCOVER_CLASSES[59].color).toBe('#1F8D49');
    expect(LANDCOVER_CLASSES[33].color).toBe('#2532E4');
    expect(LANDCOVER_CLASSES[27].color).toBe('#FFFFFF');
  });

  it('colours an available comuna by its dominant class', () => {
    expect(landcoverColorFor(entry(9, 'Silvicultura', 0.6))).toBe('#7A5900');
    expect(landcoverColorFor(entry(66, 'Matorral', 0.5))).toBe('#A89358');
  });

  it('falls back to the neutral fill for no data or an unknown class', () => {
    expect(landcoverColorFor(NO_DATA)).toBe(NEUTRAL_FILL);
    expect(landcoverColorFor(undefined)).toBe(NEUTRAL_FILL);
    expect(landcoverColorFor(entry(999, 'Otra', 0.5))).toBe(NEUTRAL_FILL);
    expect(landcoverColorFor({ ...entry(9, 'Silvicultura', 0.6), available: false })).toBe(NEUTRAL_FILL);
  });
});

describe('landcoverLegendClasses', () => {
  const layer = {
    values: {
      a: entry(9, 'Silvicultura', 0.6),
      b: entry(9, 'Silvicultura', 0.5),
      c: entry(66, 'Matorral', 0.4),
      d: entry(59, 'Bosque Primario', 0.7),
      e: NO_DATA,
      f: entry(999, 'Desconocida', 0.5)
    }
  };

  it('lists only the classes present, most frequent first, with official colours', () => {
    expect(landcoverLegendClasses(layer)).toEqual([
      { code: 9, label: 'Silvicultura', color: '#7A5900', count: 2 },
      { code: 59, label: 'Bosque Primario', color: '#1F8D49', count: 1 },
      { code: 66, label: 'Matorral', color: '#A89358', count: 1 }
    ]);
  });

  it('returns an empty list without a layer', () => {
    expect(landcoverLegendClasses(null)).toEqual([]);
    expect(landcoverLegendClasses({})).toEqual([]);
  });
});

describe('landcoverLegendNote', () => {
  const layer = { year: 2024, values: { a: entry(9, 'Silvicultura', 0.6), b: entry(66, 'Matorral', 0.4), c: NO_DATA } };

  it('states the year and how many comunas have a recorded dominant class', () => {
    expect(landcoverLegendNote(layer, { hasYearControl: true })).toBe(
      'Año 2024 · 2 de 3 comunas con clase dominante registrada (1 sin datos). Usa el selector para explorar otros años.'
    );
  });

  it('omits the unavailable remark and the selector sentence when not needed', () => {
    const full = { year: 1999, values: { a: entry(9, 'Silvicultura', 0.6) } };
    expect(landcoverLegendNote(full)).toBe('Año 1999 · 1 de 1 comuna con clase dominante registrada.');
  });

  it('returns an empty string without a usable layer or year', () => {
    expect(landcoverLegendNote(null)).toBe('');
    expect(landcoverLegendNote({ values: {} })).toBe('');
  });
});

describe('landcoverLineFor', () => {
  it('builds the dominant-cover line for the risk tooltip', () => {
    expect(landcoverLineFor(entry(9, 'Silvicultura', 0.6), 2024)).toBe('Cobertura 2024: Silvicultura 60,00 %');
  });

  it('says there is no data when unavailable, missing or incomplete', () => {
    expect(landcoverLineFor(NO_DATA, 2024)).toBe('Sin datos de cobertura');
    expect(landcoverLineFor(undefined, 2024)).toBe('Sin datos de cobertura');
    expect(landcoverLineFor({ available: true, dominantLabel: null, dominantShare: 0.5 }, 2024)).toBe(
      'Sin datos de cobertura'
    );
  });
});

describe('landcoverTooltipLines', () => {
  const top = [
    { code: 9, label: 'Silvicultura', share: 0.6 },
    { code: 66, label: 'Matorral', share: 0.2499 },
    { code: 12, label: 'Pastizal', share: 0.1 },
    { code: 15, label: 'Pastura', share: 0.05 }
  ];

  it('shows comuna, year and at most the top 3 classes with truncated percentages', () => {
    expect(landcoverTooltipLines({ nombre: 'Tomé', year: 2024, entry: entry(9, 'Silvicultura', 0.6, top) })).toEqual([
      'Tomé',
      'Cobertura 2024',
      'Silvicultura: 60,00 %',
      'Matorral: 24,99 %',
      'Pastizal: 10,00 %'
    ]);
  });

  it('skips malformed top entries', () => {
    const odd = [{ code: 9, label: null, share: 0.6 }, { code: 66, label: 'Matorral', share: -1 }, top[2]];
    expect(landcoverTooltipLines({ nombre: 'Tomé', year: 2024, entry: entry(9, 'Silvicultura', 0.6, odd) })).toEqual([
      'Tomé',
      'Cobertura 2024',
      'Pastizal: 10,00 %'
    ]);
  });

  it('reports no data for an unavailable or missing comuna', () => {
    const expected = ['Tomé', 'Sin datos de cobertura en 2024'];
    expect(landcoverTooltipLines({ nombre: 'Tomé', year: 2024, entry: NO_DATA })).toEqual(expected);
    expect(landcoverTooltipLines({ nombre: 'Tomé', year: 2024, entry: undefined })).toEqual(expected);
  });
});

describe('landcoverToggleLabel', () => {
  it('shows the plain label until a layer is loaded, then the year', () => {
    expect(landcoverToggleLabel(null)).toBe('Cobertura (MapBiomas)');
    expect(landcoverToggleLabel({ year: 2024, values: {} })).toBe('Cobertura (MapBiomas) · 2024');
  });
});
