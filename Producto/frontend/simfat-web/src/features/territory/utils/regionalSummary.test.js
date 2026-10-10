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

  it('treats a missing or unknown alert level as normal', () => {
    const summary = regionalSummary({
      comunaScores: { a: { scoreComposite: 0.2 }, b: score(0.2, 'XYZ', 'Beta') }
    });
    expect(summary.alertText).toBe('Sin comunas en alerta');
    expect(summary.distribution).toEqual([{ level: 'NORMAL', label: 'Normal', count: 2 }]);
  });

  it('ignores entries without a numeric score', () => {
    const summary = regionalSummary({
      comunaScores: { a: score(0.4, 'NORMAL', 'Alfa'), b: { alertLevel: 'ALTO' }, c: null, d: score(null, 'ALTO', 'D') }
    });
    expect(summary.comunaCount).toBe(1);
    expect(summary.alertText).toBe('Sin comunas en alerta');
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

  it('passes through valid counts and drops invalid ones', () => {
    const ok = regionalSummary({ comunaScores: {}, firmsCount48h: 3, reports7d: 0 });
    expect(ok.firmsCount48h).toBe(3);
    expect(ok.reports7d).toBe(0);
    const bad = regionalSummary({ comunaScores: {}, firmsCount48h: undefined, reports7d: 'x' });
    expect(bad.firmsCount48h).toBeNull();
    expect(bad.reports7d).toBeNull();
  });
});
