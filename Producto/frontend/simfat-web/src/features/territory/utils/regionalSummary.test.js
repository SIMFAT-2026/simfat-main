import { describe, expect, it } from 'vitest';
import { regionalSummary } from './regionalSummary';

const score = (scoreComposite, alertLevel, nombreComuna) => ({ scoreComposite, alertLevel, nombreComuna });

describe('regionalSummary', () => {
  it('handles empty or missing input', () => {
    for (const comunaScores of [undefined, null, {}]) {
      const summary = regionalSummary({ comunaScores });
      expect(summary.comunaCount).toBe(0);
      expect(summary.max).toBeNull();
      expect(summary.top).toEqual([]);
      expect(summary.distribution).toEqual([]);
      expect(summary.alertText).toBe('Sin datos por comuna');
      expect(summary.firmsCount48h).toBeNull();
      expect(summary.reports7d).toBeNull();
    }
  });

  it('reports no comunas in alert when all are normal', () => {
    const summary = regionalSummary({
      comunaScores: { a: score(0.1, 'NORMAL', 'Alfa'), b: score(0.3, 'NORMAL', 'Beta') }
    });
    expect(summary.comunaCount).toBe(2);
    expect(summary.alertText).toBe('Sin comunas en alerta');
    expect(summary.distribution).toEqual([{ level: 'NORMAL', label: 'Normal', count: 2 }]);
  });

  it('counts only the highest non-normal level present in the headline', () => {
    const summary = regionalSummary({
      comunaScores: {
        a: score(0.75, 'ALTO', 'Alfa'),
        b: score(0.8, 'ALTO', 'Beta'),
        c: score(0.55, 'PREVENTIVO', 'Gama'),
        d: score(0.1, 'NORMAL', 'Delta')
      }
    });
    expect(summary.alertText).toBe('2 en nivel alto');
    expect(summary.distribution).toEqual([
      { level: 'ALTO', label: 'Alto', count: 2 },
      { level: 'PREVENTIVO', label: 'Preventivo', count: 1 },
      { level: 'NORMAL', label: 'Normal', count: 1 }
    ]);
  });

  it('prefers critical over high and keeps the label in singular', () => {
    const summary = regionalSummary({
      comunaScores: { a: score(0.9, 'CRITICO', 'Alfa'), b: score(0.8, 'ALTO', 'Beta') }
    });
    expect(summary.alertText).toBe('1 en nivel crítico');
  });

  it('leaves a missing or unknown alert level out of the distribution instead of counting it as normal', () => {
    const summary = regionalSummary({
      comunaScores: { a: { scoreComposite: 0.2 }, b: score(0.2, 'XYZ', 'Beta'), c: score(0.1, 'NORMAL', 'Gama') },
      comunaTotal: 3
    });
    expect(summary.distribution).toEqual([{ level: 'NORMAL', label: 'Normal', count: 1 }]);
    expect(summary.comunaCount).toBe(1);
    expect(summary.withoutDataCount).toBe(2);
    expect(summary.top.map((item) => item.name)).toEqual(['Gama']);
  });

  it('does not claim "no comunas in alert" without qualification when the level is unknown and no total is given', () => {
    const summary = regionalSummary({ comunaScores: { a: score(0.2, 'NORMAL', 'Alfa'), b: score(0.2, 'XYZ', 'Beta') } });
    expect(summary.alertText).toBe('Sin comunas en alerta entre las que tienen puntaje');
  });

  it('reports "N de M comunas con puntaje" when some comunas have no score', () => {
    const summary = regionalSummary({
      comunaScores: { a: score(0.1, 'NORMAL', 'Alfa'), b: score(0.3, 'NORMAL', 'Beta') },
      comunaTotal: 5
    });
    expect(summary.comunasText).toBe('2 de 5 comunas con puntaje');
    expect(summary.withoutDataCount).toBe(3);
    expect(summary.alertText).toBe('Sin comunas en alerta entre las que tienen puntaje');
  });

  it('reports "M comunas" when every comuna has a score', () => {
    const summary = regionalSummary({
      comunaScores: { a: score(0.1, 'NORMAL', 'Alfa'), b: score(0.3, 'NORMAL', 'Beta') },
      comunaTotal: 2
    });
    expect(summary.comunasText).toBe('2 comunas');
    expect(summary.withoutDataCount).toBe(0);
    expect(summary.alertText).toBe('Sin comunas en alerta');
  });

  it('falls back to the scored count when the total is unknown or smaller', () => {
    const scores = { a: score(0.1, 'NORMAL', 'Alfa') };
    expect(regionalSummary({ comunaScores: scores }).comunasText).toBe('1 comuna');
    expect(regionalSummary({ comunaScores: scores, comunaTotal: 0 }).comunasText).toBe('1 comuna');
    expect(regionalSummary({ comunaScores: scores, comunaTotal: 'x' }).comunasText).toBe('1 comuna');
    expect(regionalSummary({ comunaScores: {}, comunaTotal: 4 }).comunasText).toBe('');
  });

  it('still names the highest alert level when some comunas lack data', () => {
    const summary = regionalSummary({
      comunaScores: { a: score(0.8, 'ALTO', 'Alfa') },
      comunaTotal: 3
    });
    expect(summary.alertText).toBe('1 en nivel alto');
  });

  it('ignores entries without a numeric score', () => {
    const summary = regionalSummary({
      comunaScores: { a: score(0.4, 'NORMAL', 'Alfa'), b: { alertLevel: 'ALTO' }, c: null, d: score(null, 'ALTO', 'D') }
    });
    expect(summary.comunaCount).toBe(1);
    expect(summary.alertText).toBe('Sin comunas en alerta');
  });

  it('breaks ties on the displayed rounded value, not the raw score', () => {
    const summary = regionalSummary({
      comunaScores: { z: score(0.474, 'NORMAL', 'Zeta'), a: score(0.466, 'NORMAL', 'Alfa'), m: score(0.48, 'NORMAL', 'Mu') }
    });
    // 47, 47 and 48 once rounded: Mu leads alone; Alfa and Zeta tie and sort by name.
    expect(summary.max.name).toBe('Mu');
    expect(summary.maxTies).toBe(0);
    expect(summary.top.map((item) => `${item.name} ${item.pct}`)).toEqual(['Mu 48', 'Alfa 47', 'Zeta 47']);
  });

  it('counts max ties among comunas that display the same rounded value', () => {
    const summary = regionalSummary({
      comunaScores: { z: score(0.474, 'NORMAL', 'Zeta'), a: score(0.466, 'NORMAL', 'Alfa'), c: score(0.1, 'NORMAL', 'Gama') }
    });
    expect(summary.max.name).toBe('Alfa');
    expect(summary.maxTies).toBe(1);
  });

  it('picks the max comuna and rounds like the badge', () => {
    const summary = regionalSummary({
      comunaScores: { a: score(0.474, 'NORMAL', 'Arauco'), b: score(0.2, 'NORMAL', 'Beta') }
    });
    expect(summary.max).toEqual({ comunaId: 'a', name: 'Arauco', pct: '47', level: 'NORMAL', label: 'Normal' });
    expect(summary.maxTies).toBe(0);
  });

  it('breaks ties by name and reports how many others share the max', () => {
    const summary = regionalSummary({
      comunaScores: { z: score(0.5, 'PREVENTIVO', 'Zeta'), a: score(0.5, 'PREVENTIVO', 'Alfa'), c: score(0.1, 'NORMAL', 'Gama') }
    });
    expect(summary.max.name).toBe('Alfa');
    expect(summary.maxTies).toBe(1);
    expect(summary.top.map((item) => item.name)).toEqual(['Alfa', 'Zeta', 'Gama']);
  });

  it('prefers comunaNames, then nombreComuna, then the id', () => {
    const summary = regionalSummary({
      comunaScores: { a: score(0.9, 'CRITICO', 'Server'), b: score(0.8, 'ALTO', 'Beta'), c: score(0.7, 'ALTO') },
      comunaNames: { a: 'Geo A' }
    });
    expect(summary.top.map((item) => item.name)).toEqual(['Geo A', 'Beta', 'c']);
  });

  it('limits the top list to five comunas', () => {
    const comunaScores = {};
    for (let i = 0; i < 8; i += 1) comunaScores[`c${i}`] = score(i / 10, 'NORMAL', `C${i}`);
    expect(regionalSummary({ comunaScores }).top).toHaveLength(5);
  });

  it('uses the backend alertDriver for the trigger breakdown when present', () => {
    const summary = regionalSummary({
      comunaScores: {
        a: { ...score(0.9, 'CRITICO', 'Alfa'), fwiRaw: 50, alertDriver: 'FIRMS_TODAY' },
        b: { ...score(0.9, 'CRITICO', 'Beta'), fwiRaw: 50, alertDriver: 'FWI' },
        c: { ...score(0.9, 'CRITICO', 'Gama'), fwiRaw: 10, alertDriver: 'SCORE' }
      }
    });
    expect(summary.alertText).toBe('3 en nivel crítico (1 por FWI ≥ 45, 1 por focos FIRMS)');
  });

  it('says how many comunas in the top level got there by FWI', () => {
    const fwiAlto = (nombre, fwiRaw) => ({ ...score(0.25, 'ALTO', nombre), fwiRaw, firmsCount: 0, firmsFrpMean: 0 });
    const summary = regionalSummary({
      comunaScores: {
        a: fwiAlto('Alfa', 21.5),
        b: fwiAlto('Beta', 20),
        c: score(0.74, 'ALTO', 'Gama'),
        d: score(0.1, 'NORMAL', 'Delta')
      }
    });
    expect(summary.alertText).toBe('3 en nivel alto (2 por FWI ≥ 20)');
  });

  it('omits the trigger breakdown when every top-level comuna is there by score', () => {
    const summary = regionalSummary({
      comunaScores: { a: score(0.75, 'ALTO', 'Alfa'), b: score(0.8, 'ALTO', 'Beta') }
    });
    expect(summary.alertText).toBe('2 en nivel alto');
  });

  it('breaks the critical level down by FWI and FIRMS triggers', () => {
    const summary = regionalSummary({
      comunaScores: {
        a: { ...score(0.9, 'CRITICO', 'Alfa'), fwiRaw: 10, firmsCount: 0, firmsFrpMean: 0 },
        b: { ...score(0.3, 'CRITICO', 'Beta'), fwiRaw: 12, firmsCount: 5, firmsFrpMean: 10 },
        c: { ...score(0.4, 'CRITICO', 'Gama'), fwiRaw: 47, firmsCount: 0, firmsFrpMean: 0 },
        d: { ...score(0.3, 'ALTO', 'Delta'), fwiRaw: 25 }
      }
    });
    expect(summary.alertText).toBe('3 en nivel crítico (1 por FWI ≥ 45, 1 por focos FIRMS)');
  });

  it('does not count FWI triggers for levels below the headline level', () => {
    const summary = regionalSummary({
      comunaScores: {
        a: { ...score(0.9, 'CRITICO', 'Alfa'), fwiRaw: 10 },
        b: { ...score(0.3, 'ALTO', 'Beta'), fwiRaw: 25 }
      }
    });
    expect(summary.alertText).toBe('1 en nivel crítico');
  });

  it('passes through valid counts and drops invalid ones', () => {
    const ok = regionalSummary({ comunaScores: {}, firmsCount48h: 3, reports7d: 0 });
    expect(ok.firmsCount48h).toBe(3);
    expect(ok.reports7d).toBe(0);
    const bad = regionalSummary({ comunaScores: {}, firmsCount48h: undefined, reports7d: 'x' });
    expect(bad.firmsCount48h).toBeNull();
    expect(bad.reports7d).toBeNull();
  });

  it('restores curated names when the score only carries the GADM joined name', () => {
    const summary = regionalSummary({
      comunaScores: { 'CHL.6.2.5_1': score(0.9, 'ALTO', 'LosAngeles') }
    });
    expect(summary.top[0].name).toBe('Los Ángeles');
  });
});
