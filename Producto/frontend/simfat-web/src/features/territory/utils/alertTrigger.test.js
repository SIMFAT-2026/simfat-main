import { describe, expect, it } from 'vitest';
import { ALERT_RULE_EXPLANATION, FWI_ALTO, FWI_CRITICO, alertTrigger } from './alertTrigger';

const base = { alertLevel: 'NORMAL', score: 0.2, fwiRaw: 5, firmsCount: 0, firmsFrpMean: 0 };

describe('ALERT_RULE_EXPLANATION', () => {
  it('separates the score from the alert level and names the triggers', () => {
    expect(ALERT_RULE_EXPLANATION).toBe(
      'El puntaje (0–100) combina clima (FWI), vegetación, focos y reportes. El nivel de alerta sube por el puntaje o por disparadores: FWI ≥ 20 eleva a Alto; focos FIRMS cercanos o intensos elevan a Crítico.'
    );
  });
});

describe('alertTrigger', () => {
  it('mirrors the backend FWI thresholds', () => {
    expect(FWI_ALTO).toBe(20);
    expect(FWI_CRITICO).toBe(45);
  });

  it('reports the score for NORMAL and PREVENTIVO levels', () => {
    expect(alertTrigger({ ...base })).toEqual({ kind: 'score', text: '' });
    expect(alertTrigger({ ...base, alertLevel: 'PREVENTIVO', score: 0.55 })).toEqual({ kind: 'score', text: '' });
  });

  it('attributes ALTO to the score when it reaches 0.70, even with a high FWI', () => {
    expect(alertTrigger({ ...base, alertLevel: 'ALTO', score: 0.7, fwiRaw: 30 }).kind).toBe('score');
    expect(alertTrigger({ ...base, alertLevel: 'ALTO', score: 0.78, fwiRaw: 5 }).kind).toBe('score');
  });

  it('attributes ALTO to the FWI when it is at least 20 and the score is below 0.70', () => {
    expect(alertTrigger({ ...base, alertLevel: 'ALTO', score: 0.2146, fwiRaw: 23.4 })).toEqual({
      kind: 'fwi',
      text: 'Activado por FWI 23,4 (peligro meteorológico)'
    });
  });

  it('treats FWI exactly 20 as a trigger and 19.99 as not', () => {
    expect(alertTrigger({ ...base, alertLevel: 'ALTO', score: 0.2, fwiRaw: 20 }).kind).toBe('fwi');
    expect(alertTrigger({ ...base, alertLevel: 'ALTO', score: 0.2, fwiRaw: 19.99 }).kind).toBe('score');
  });

  it('treats score exactly 0.70 as the score and 0.6999 as the FWI', () => {
    expect(alertTrigger({ ...base, alertLevel: 'ALTO', score: 0.7, fwiRaw: 20 }).kind).toBe('score');
    expect(alertTrigger({ ...base, alertLevel: 'ALTO', score: 0.6999, fwiRaw: 20 }).kind).toBe('fwi');
  });

  it('formats the FWI with one decimal and a decimal comma', () => {
    expect(alertTrigger({ ...base, alertLevel: 'ALTO', fwiRaw: 20 }).text).toBe('Activado por FWI 20,0 (peligro meteorológico)');
    expect(alertTrigger({ ...base, alertLevel: 'ALTO', fwiRaw: 20.63 }).text).toBe('Activado por FWI 20,6 (peligro meteorológico)');
  });

  it('attributes CRITICO to the score when it reaches 0.85', () => {
    expect(alertTrigger({ ...base, alertLevel: 'CRITICO', score: 0.85, fwiRaw: 50, firmsCount: 9 }).kind).toBe('score');
  });

  it('attributes CRITICO to the FWI at 45 or more when the score is below 0.85', () => {
    expect(alertTrigger({ ...base, alertLevel: 'CRITICO', score: 0.5, fwiRaw: 45 })).toEqual({
      kind: 'fwi',
      text: 'Activado por FWI 45,0 (peligro meteorológico)'
    });
  });

  it('attributes CRITICO to FIRMS when the count or the mean FRP reach the backend thresholds', () => {
    expect(alertTrigger({ ...base, alertLevel: 'CRITICO', score: 0.3, firmsCount: 4 })).toEqual({
      kind: 'firms',
      text: 'Activado por focos FIRMS'
    });
    expect(alertTrigger({ ...base, alertLevel: 'CRITICO', score: 0.3, firmsFrpMean: 60 }).kind).toBe('firms');
  });

  it('falls back to a generic FIRMS trigger for CRITICO below the score threshold with no other cause', () => {
    // The client cannot see "FIRMS detection today", the only remaining cause.
    expect(alertTrigger({ ...base, alertLevel: 'CRITICO', score: 0.3, firmsCount: 1 })).toEqual({
      kind: 'firms',
      text: 'Activado por focos FIRMS'
    });
  });

  it('prefers the FWI over the FIRMS count for CRITICO, following the backend rule order', () => {
    expect(alertTrigger({ ...base, alertLevel: 'CRITICO', score: 0.4, fwiRaw: 46, firmsCount: 6 }).kind).toBe('fwi');
  });

  it('stays quiet with missing data', () => {
    expect(alertTrigger()).toEqual({ kind: null, text: '' });
    expect(alertTrigger({})).toEqual({ kind: null, text: '' });
    expect(alertTrigger({ alertLevel: 'XYZ', score: 0.9 })).toEqual({ kind: null, text: '' });
  });

  it('handles null inputs without throwing', () => {
    expect(alertTrigger({ alertLevel: 'ALTO', score: null, fwiRaw: null })).toEqual({ kind: null, text: '' });
    expect(alertTrigger({ alertLevel: 'ALTO', score: 0.2, fwiRaw: null }).kind).toBe('score');
    expect(alertTrigger({ alertLevel: 'ALTO', score: null, fwiRaw: 25 }).kind).toBe('fwi');
    expect(alertTrigger({ alertLevel: 'CRITICO', score: null, fwiRaw: null, firmsCount: null, firmsFrpMean: null })).toEqual({
      kind: null,
      text: ''
    });
    expect(alertTrigger({ alertLevel: 'CRITICO', score: 0.3, fwiRaw: null, firmsCount: null, firmsFrpMean: null }).kind).toBe(
      'firms'
    );
  });
});
