import { describe, expect, it } from 'vitest';
import {
  comunaMapbiomasCacheKey,
  comunaMapbiomasErrorView,
  coverageView,
  fireView,
  boundedCacheSet,
  comunaPanelOrigin,
  formatIndex,
  formatWeight,
  panelMode,
  readFreshCache,
  staleDataFor,
  indicesRows,
  panelYear,
  scoreSentences
} from './comunaMapbiomas';

const shares = [
  { code: 9, label: 'Silvicultura', share: 0.6045 },
  { code: 15, label: 'Pastura', share: 0.1463 },
  { code: 60, label: 'Bosque Secundario', share: 0.0799 },
  { code: 66, label: 'Matorral', share: 0.0658 },
  { code: 59, label: 'Bosque Primario', share: 0.0332 },
  { code: 18, label: 'Agricultura', share: 0.0329 },
  { code: 24, label: 'Infraestructura', share: 0.017 }
];

describe('comunaMapbiomasCacheKey', () => {
  it('keeps the default year in its own slot', () => {
    expect(comunaMapbiomasCacheKey('CHL.6.1.1_1', 2024)).toBe('CHL.6.1.1_1|2024');
    expect(comunaMapbiomasCacheKey('CHL.6.1.1_1')).toBe('CHL.6.1.1_1|default');
    expect(comunaMapbiomasCacheKey('CHL.6.1.1_1', null)).toBe('CHL.6.1.1_1|default');
  });
});

describe('panelYear', () => {
  it('uses the landcover year only while that layer is visible and has one', () => {
    expect(panelYear({ visible: true, selectedYear: 2020 })).toBe(2020);
    expect(panelYear({ visible: false, selectedYear: 2020 })).toBeUndefined();
    expect(panelYear({ visible: true, selectedYear: undefined })).toBeUndefined();
    expect(panelYear({ visible: true, selectedYear: 'x' })).toBeUndefined();
    expect(panelYear()).toBeUndefined();
  });
});

describe('coverageView', () => {
  it('shows the top 5 classes with palette colour, whole percent and an Otras remainder', () => {
    const view = coverageView({ available: true, year: 2024, shares, unobservedShare: 0 });
    expect(view.state).toBe('ready');
    expect(view.title).toBe('Cobertura 2024');
    expect(view.bars.map((bar) => bar.label)).toEqual([
      'Silvicultura',
      'Pastura',
      'Bosque Secundario',
      'Matorral',
      'Bosque Primario'
    ]);
    expect(view.bars[0]).toMatchObject({ code: 9, color: '#7A5900', text: '60 %' });
    expect(view.others.text).toBe('5 %');
    expect(view.unobservedText).toBeNull();
    expect(view.summary).toContain('Silvicultura 60 %');
    expect(view.summary).toContain('Otras 5 %');
  });

  it('omits Otras when there are at most 5 classes', () => {
    const view = coverageView({ available: true, year: 2024, shares: shares.slice(0, 3) });
    expect(view.others).toBeNull();
  });

  it('shows No observado only from 1 %', () => {
    expect(coverageView({ available: true, year: 2024, shares, unobservedShare: 0.0099 }).unobservedText).toBeNull();
    expect(coverageView({ available: true, year: 2024, shares, unobservedShare: 0.12 }).unobservedText).toBe(
      'No observado 12 %'
    );
  });

  it('falls back to a neutral colour for classes outside the palette', () => {
    const view = coverageView({ available: true, year: 2024, shares: [{ code: 999, label: 'Rara', share: 0.5 }] });
    expect(view.bars[0].color).toMatch(/^#/);
    expect(view.bars[0].label).toBe('Rara');
  });

  it('reports no data when unavailable or without shares', () => {
    expect(coverageView({ available: false, year: 2024, shares: null })).toEqual({
      state: 'empty',
      message: 'Sin datos de cobertura para 2024'
    });
    expect(coverageView({ available: true, year: 2024, shares: [] }).state).toBe('empty');
    expect(coverageView(null).message).toBe('Sin datos de cobertura');
  });
});

describe('fireView', () => {
  const fire = {
    available: true,
    burnedHaByYear: { 2014: 10, 2013: 40, 2015: 0 },
    burnedFraction: 0.0872,
    yearLastFire: 2014
  };

  it('builds sorted bars scaled to the maximum and highlights the layer year', () => {
    const view = fireView(fire, true, 2014);
    expect(view.state).toBe('ready');
    expect(view.bars.map((bar) => bar.year)).toEqual([2013, 2014, 2015]);
    expect(view.bars[0].heightPct).toBe(100);
    expect(view.bars[1].heightPct).toBe(25);
    expect(view.bars[2].heightPct).toBe(0);
    expect(view.bars.map((bar) => bar.highlighted)).toEqual([false, true, false]);
    expect(view.title).toBe('Fuego 2013–2015');
    expect(view.cumulativeText).toBe('Área quemada acumulada 2013–2015: 8,72 %');
    expect(view.lastFireText).toBe('Último año con fuego: 2014');
    expect(view.summary).toContain('2013: 40 ha');
  });

  it('keeps tiny non-zero years visible', () => {
    const view = fireView({ ...fire, burnedHaByYear: { 2013: 100000, 2014: 1 } }, true);
    expect(view.bars[1].heightPct).toBeGreaterThanOrEqual(4);
  });

  it('labels the first and last year and the selected year', () => {
    const view = fireView(fire, true, 2014);
    expect(view.firstYearLabel).toBe('2013');
    expect(view.lastYearLabel).toBe('2015');
    expect(view.selectedText).toBe('Año seleccionado 2014: 10 ha');
    expect(fireView(fire, true).selectedText).toBeNull();
    expect(fireView(fire, true, 1999).selectedText).toBeNull();
  });

  it('gives a highlighted 0 ha bar a visible minimum marker', () => {
    const view = fireView(fire, true, 2015);
    expect(view.bars[2].highlighted).toBe(true);
    expect(view.bars[2].heightPct).toBeGreaterThanOrEqual(4);
    expect(view.bars[0].heightPct).toBe(100);
    expect(fireView(fire, true, 2014).bars[2].heightPct).toBe(0);
  });

  it('reports no history', () => {
    expect(fireView(fire, false)).toEqual({ state: 'empty', message: 'Sin historial de fuego disponible' });
    expect(fireView(null, true).state).toBe('empty');
    expect(fireView({ available: true, burnedHaByYear: {} }, true).state).toBe('empty');
  });

  it('says so when no fire year is known', () => {
    expect(fireView({ ...fire, yearLastFire: null }, true).lastFireText).toBe('Sin fuego registrado en el período');
  });
});

describe('formatIndex', () => {
  it('uses 2 decimals es-CL and a dash for missing values', () => {
    expect(formatIndex(0.6456)).toBe('0,65');
    expect(formatIndex(0)).toBe('0,00');
    expect(formatIndex(null)).toBe('—');
    expect(formatIndex(undefined)).toBe('—');
    expect(formatIndex(Number.NaN)).toBe('—');
  });
});

describe('indicesRows', () => {
  it('lists the three indices in order', () => {
    expect(indicesRows({ fuelIndex: 0.786, historyIndex: 0.38, sMapbiomas: null })).toEqual([
      { label: 'Combustible', value: '0,79' },
      { label: 'Historial de fuego', value: '0,38' },
      { label: 'S_MapBiomas', value: '—' }
    ]);
    expect(indicesRows(null).every((row) => row.value === '—')).toBe(true);
  });
});

describe('formatWeight', () => {
  it('shows up to 3 decimals without trailing zeros', () => {
    expect(formatWeight(0.125)).toBe('0,125');
    expect(formatWeight(0.5)).toBe('0,5');
    expect(formatWeight(0.1)).toBe('0,1');
    expect(formatWeight('0.25')).toBe('0,25');
    expect(formatWeight(1)).toBe('1');
  });
  it('returns a dash when not numeric', () => {
    expect(formatWeight(null)).toBe('—');
    expect(formatWeight('abc')).toBe('—');
  });
});

describe('scoreSentences', () => {
  const CURRENT = 'El riesgo es el valor actual y no depende del año seleccionado en el mapa.';
  const NEUTRAL = 'No se pudo determinar si MapBiomas entra al cálculo del riesgo.';
  const ZERO = 'MapBiomas no entra hoy al cálculo del riesgo (peso 0); se muestra como contexto.';
  const MISSING = 'MapBiomas no se usó en el riesgo de esta comuna por falta de datos válidos.';

  it('states the weight when MapBiomas entered the score, without claiming an added amount', () => {
    const text = scoreSentences({ enteredInScore: true, weight: 0.1, weightedComponent: 0.06 }).join(' ');
    expect(text).toContain('MapBiomas aporta al riesgo con peso 0,1.');
    expect(text).toContain('uno de los componentes');
    expect(text).not.toMatch(/suma/);
    expect(text).not.toContain('0,06');
  });

  it('coerces numeric string weights', () => {
    expect(scoreSentences({ enteredInScore: true, weight: '0.125' })[0]).toBe('MapBiomas aporta al riesgo con peso 0,125.');
  });

  it('explains weight 0 as context only, regardless of enteredInScore', () => {
    expect(scoreSentences({ enteredInScore: false, weight: 0 })[0]).toBe(ZERO);
    expect(scoreSentences({ enteredInScore: true, weight: 0 })[0]).toBe(ZERO);
    expect(scoreSentences({ enteredInScore: true, weight: '0' })[0]).toBe(ZERO);
  });

  it('does not claim a contribution when enteredInScore is not strictly true', () => {
    expect(scoreSentences({ enteredInScore: 'true', weight: 0.1, sMapbiomas: null })[0]).toBe(MISSING);
    expect(scoreSentences({ weight: 0.1, sMapbiomas: null })[0]).toBe(MISSING);
  });

  it('explains missing valid data only when weight > 0, not entered and sMapbiomas is null', () => {
    expect(scoreSentences({ enteredInScore: false, weight: 0.1, sMapbiomas: null })[0]).toBe(MISSING);
    expect(scoreSentences({ enteredInScore: false, weight: 0.1, sMapbiomas: 0.4 })[0]).toBe(NEUTRAL);
  });

  it('is neutral when the weight is unknown', () => {
    expect(scoreSentences({ enteredInScore: true, weight: null })[0]).toBe(NEUTRAL);
    expect(scoreSentences({ enteredInScore: true })[0]).toBe(NEUTRAL);
    expect(scoreSentences({ enteredInScore: true, weight: 'abc' })[0]).toBe(NEUTRAL);
    expect(scoreSentences({ enteredInScore: false, weight: undefined })[0]).toBe(NEUTRAL);
    expect(scoreSentences(null)[0]).toBe(NEUTRAL);
    expect(scoreSentences({ enteredInScore: true, weight: -1 })[0]).toBe(NEUTRAL);
  });

  it('always says the score is current and independent of the selected year', () => {
    for (const indices of [{ enteredInScore: false, weight: 0 }, { enteredInScore: true, weight: 0.2 }, null]) {
      const sentences = scoreSentences(indices);
      expect(sentences[sentences.length - 1]).toBe(CURRENT);
    }
  });
});

describe('panelMode', () => {
  it('renders risk when there is a score', () => {
    expect(panelMode({ comunaId: 'a', score: { alertLevel: 'NORMAL' } })).toBe('risk');
  });
  it('falls back to mapbiomas-only without a score', () => {
    expect(panelMode({ comunaId: 'a', score: undefined })).toBe('mapbiomas-only');
    expect(panelMode({ comunaId: 'a', score: null })).toBe('mapbiomas-only');
  });
  it('is none without a comuna', () => {
    expect(panelMode({ comunaId: null, score: {} })).toBe('none');
    expect(panelMode({})).toBe('none');
    expect(panelMode()).toBe('none');
  });
});

describe('comunaPanelOrigin', () => {
  it('opens from risk only when the risk layer owns the click', () => {
    expect(comunaPanelOrigin('RISK_SCORE')).toBe('risk');
    expect(comunaPanelOrigin('BURNED')).toBe('mapbiomas');
    expect(comunaPanelOrigin('LANDCOVER')).toBe('mapbiomas');
    expect(comunaPanelOrigin(null)).toBe('risk');
  });
});

describe('boundedCacheSet / readFreshCache', () => {
  it('evicts the oldest entry beyond the cap', () => {
    const cache = new Map();
    for (let i = 0; i < 4; i += 1) boundedCacheSet(cache, 'k' + i, { data: i, expiresAt: 10 }, 3);
    expect([...cache.keys()]).toEqual(['k1', 'k2', 'k3']);
  });
  it('re-setting a key refreshes its position', () => {
    const cache = new Map();
    boundedCacheSet(cache, 'a', 1, 2);
    boundedCacheSet(cache, 'b', 2, 2);
    boundedCacheSet(cache, 'a', 3, 2);
    boundedCacheSet(cache, 'c', 4, 2);
    expect([...cache.keys()]).toEqual(['a', 'c']);
  });
  it('reads only fresh entries and drops expired ones', () => {
    const cache = new Map([['a', { data: 'x', expiresAt: 100 }]]);
    expect(readFreshCache(cache, 'a', 50)).toBe('x');
    expect(readFreshCache(cache, 'a', 100)).toBeNull();
    expect(cache.has('a')).toBe(false);
    expect(readFreshCache(cache, 'zz', 0)).toBeNull();
  });
});

describe('staleDataFor', () => {
  const prev = { comunaId: 'c1', data: { x: 1 } };
  it('keeps previous data only for the same comuna', () => {
    expect(staleDataFor(prev, 'c1')).toEqual({ x: 1 });
    expect(staleDataFor(prev, 'c2')).toBeNull();
    expect(staleDataFor(null, 'c1')).toBeNull();
    expect(staleDataFor({ comunaId: 'c1', data: null }, 'c1')).toBeNull();
  });
});

describe('comunaMapbiomasErrorView', () => {
  it('maps 404 to a no-retry message and anything else to a fixed retryable one', () => {
    expect(comunaMapbiomasErrorView({ status: 404 })).toEqual({
      message: 'Sin datos MapBiomas para esta comuna.',
      canRetry: false
    });
    const generic = comunaMapbiomasErrorView(new Error('Network Error'));
    expect(generic.canRetry).toBe(true);
    expect(generic.message).toBe('No fue posible cargar los datos MapBiomas.');
    expect(comunaMapbiomasErrorView({ status: 500 }).canRetry).toBe(true);
  });

  it('treats 400 like missing data for the year, without retry', () => {
    expect(comunaMapbiomasErrorView({ status: 400 })).toEqual({
      message: 'Sin datos MapBiomas para este año.',
      canRetry: false
    });
  });
});
