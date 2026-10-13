import { describe, expect, it } from 'vitest';
import { buildScoreExplanation, formatWeatherRow, hasWeatherRow } from './scoreExplanation';

const MB = 'MapBiomas';
const P = 'Aporte al puntaje: ';
const STRUCT = '(susceptibilidad estructural)';

function standardScore(overrides = {}) {
  return {
    scoreComposite: 0.38,
    mode: 'STANDARD',
    fwiRaw: 7.2,
    mapbiomasWeight: 0.5,
    components: { mapbiomas: 0.31, fwi: 0.07, firms: 0, reports: 0 },
    ...overrides
  };
}

describe('buildScoreExplanation - empty inputs', () => {
  it('returns nulls for a null score', () => {
    expect(buildScoreExplanation(null)).toEqual({ rawLine: null, compositionLine: null });
  });

  it('returns a null composition when scoreComposite is not a number', () => {
    const out = buildScoreExplanation(standardScore({ scoreComposite: null }));
    expect(out.compositionLine).toBeNull();
  });

  it('returns a null composition when no segment has data', () => {
    const out = buildScoreExplanation({ scoreComposite: 0.4, components: {} });
    expect(out.compositionLine).toBeNull();
    expect(out.rawLine).toBeNull();
  });
});

describe('buildScoreExplanation - presence and order', () => {
  it('puts MapBiomas first with the structural label and keeps zeros with data', () => {
    const out = buildScoreExplanation(standardScore());
    expect(out.compositionLine).toBe(`${P}${MB} 31 ${STRUCT} · FWI 7 · Focos FIRMS 0 · Reportes 0`
    );
  });

  it('uses a fixed order, not magnitude order', () => {
    const out = buildScoreExplanation({
      scoreComposite: 0.5,
      fwiRaw: 10,
      mapbiomasWeight: 0.5,
      components: { reports: 0.04, firms: 0.1, ndvi: 0.02, ndmi: 0.08, fwi: 0.2, mapbiomas: 0.06 }
    });
    expect(out.compositionLine).toBe(`${P}${MB} 6 ${STRUCT} · FWI 20 · NDMI 8 · NDVI 2 · Focos FIRMS 10 · Reportes 4`
    );
  });

  it('hides NDMI and NDVI when their components are missing (STANDARD)', () => {
    const out = buildScoreExplanation(standardScore());
    expect(out.compositionLine).not.toMatch(/NDMI|NDVI/);
  });

  it('hides FWI when fwiRaw is missing', () => {
    const out = buildScoreExplanation(standardScore({ fwiRaw: undefined, scoreComposite: 0.31 }));
    expect(out.compositionLine).not.toMatch(/FWI/);
  });

  it('hides MapBiomas when the weight is 0 or missing', () => {
    const noWeight = buildScoreExplanation(standardScore({ mapbiomasWeight: 0, scoreComposite: 0.07 }));
    expect(noWeight.compositionLine).not.toMatch(/MapBiomas/);
    const missing = buildScoreExplanation(standardScore({ mapbiomasWeight: undefined, scoreComposite: 0.07 }));
    expect(missing.compositionLine).not.toMatch(/MapBiomas/);
  });

  it('hides MapBiomas on legacy snapshots without the component', () => {
    const out = buildScoreExplanation({
      scoreComposite: 0.07,
      fwiRaw: 3,
      components: { fwi: 0.07, firms: 0, reports: 0 }
    });
    expect(out.compositionLine).toBe('Aporte al puntaje: FWI 7 · Focos FIRMS 0 · Reportes 0');
  });

  it('hides FWI when fwiRaw is missing even if components.fwi is a number', () => {
    // The service writes components.fwi = 0 when there is no weather data.
    const out = buildScoreExplanation({
      scoreComposite: 0.31,
      mapbiomasWeight: 0.5,
      components: { mapbiomas: 0.31, fwi: 0, firms: 0 }
    });
    expect(out.compositionLine).not.toContain('FWI');
    expect(out.compositionLine).toBe(`${P}${MB} 31 ${STRUCT} · Focos FIRMS 0`);
  });

  it('never renders "sin datos" or placeholders', () => {
    const out = buildScoreExplanation({ scoreComposite: 0.2, components: { firms: null, reports: 0.2 } });
    expect(out.compositionLine).toBe('Aporte al puntaje: Reportes 20');
    expect(JSON.stringify(out)).not.toMatch(/sin datos/i);
  });
});

describe('buildScoreExplanation - rounding', () => {
  it('apportions rounded points so segments sum exactly to the displayed score', () => {
    // 12.5 + 12.5 + 12.5 = 37.5 -> score 38; plain rounding would give 39.
    const out = buildScoreExplanation({
      scoreComposite: 0.375,
      fwiRaw: 10,
      mapbiomasWeight: 0.5,
      components: { mapbiomas: 0.125, fwi: 0.125, firms: 0.125 }
    });
    expect(out.compositionLine).toBe(`${P}${MB} 13 ${STRUCT} · FWI 13 · Focos FIRMS 12`
    );
  });

  it('falls back to plain rounding with an approximate label when inconsistent', () => {
    const out = buildScoreExplanation({
      scoreComposite: 0.8,
      fwiRaw: 10,
      components: { fwi: 0.2, firms: 0.1 }
    });
    expect(out.compositionLine).toBe('Aporte al puntaje (aprox.): FWI 20 · Focos FIRMS 10');
  });
});

describe('buildScoreExplanation - raw line', () => {
  it('formats raw indices with es-CL decimal commas', () => {
    const out = buildScoreExplanation(
      standardScore({ fwiRaw: 12.34, ndmiRaw: 0.214, ndviRaw: 0.6399 })
    );
    expect(out.rawLine).toBe('FWI 12,3 · NDMI 0,21 · NDVI 0,64');
  });

  it('omits missing raw values and returns null when none exist', () => {
    expect(buildScoreExplanation(standardScore({ fwiRaw: 5 })).rawLine).toBe('FWI 5');
    expect(buildScoreExplanation(standardScore({ fwiRaw: null })).rawLine).toBeNull();
  });
});

describe('hasWeatherRow', () => {
  it('is false for missing inputs and for precip alone', () => {
    expect(hasWeatherRow(undefined)).toBe(false);
    expect(hasWeatherRow({})).toBe(false);
    expect(hasWeatherRow({ precip: 1.2 })).toBe(false);
    expect(hasWeatherRow({ precip: 1.2, tempMin: null })).toBe(false);
  });

  it('is true when any of temperature, humidity or wind is numeric', () => {
    for (const key of ['tempMin', 'tempMax', 'humidityMin', 'windMax']) {
      expect(hasWeatherRow({ [key]: 10 })).toBe(true);
    }
  });
});

describe('formatWeatherRow', () => {
  it('uses es-CL decimals and a space before units', () => {
    expect(formatWeatherRow({ tempMin: 8.2, tempMax: 20.4, humidityMin: 46, windMax: 11.3, precip: 3.7 })).toBe(
      '8°/20° · 46% · 11 km/h · 3,7 mm'
    );
  });

  it('never renders negative zero', () => {
    expect(formatWeatherRow({ tempMin: -0.4, tempMax: 5, humidityMin: 50, windMax: 3 })).toBe('0°/5° · 50% · 3 km/h');
    expect(formatWeatherRow({ tempMax: -0.2 })).toBe('0° · — · —');
    expect(formatWeatherRow({ tempMin: -3.2, tempMax: 4 })).toBe('-3°/4° · — · —');
  });

  it('omits precipitation when zero or missing and keeps placeholders', () => {
    expect(formatWeatherRow({ tempMax: 20, precip: 0 })).toBe('20° · — · —');
    expect(formatWeatherRow({ tempMin: 1, tempMax: 9, humidityMin: 80, windMax: 5 })).toBe('1°/9° · 80% · 5 km/h');
  });
});
