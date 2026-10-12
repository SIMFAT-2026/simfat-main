import { describe, expect, it } from 'vitest';
import { ALERT_RULE_EXPLANATION, FWI_ALTO, FWI_CRITICO, alertTrigger } from './alertTrigger';

const base = { alertLevel: 'NORMAL', score: 0.2, fwiRaw: 5, firmsCount: 0, firmsFrpMean: 0 };

describe('ALERT_RULE_EXPLANATION', () => {
  it('separates the score from the alert level and names the triggers', () => {
    expect(ALERT_RULE_EXPLANATION).toBe(
      'El puntaje (0–100) combina clima (FWI), vegetación, focos y reportes. El nivel de alerta sube por el puntaje o por disparadores: FWI ≥ 20 eleva a Alto; FWI ≥ 45 o un foco FIRMS detectado hoy elevan a Crítico, igual que varios focos recientes o muy intensos.'
    );
    expect(ALERT_RULE_EXPLANATION).toContain('FWI ≥ 45');
    expect(ALERT_RULE_EXPLANATION).toContain('hoy');
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

  it('attributes ALTO to the score when it reaches 0.70 and the FWI is below 20', () => {
    expect(alertTrigger({ ...base, alertLevel: 'ALTO', score: 0.7, fwiRaw: 19 }).kind).toBe('score');
    expect(alertTrigger({ ...base, alertLevel: 'ALTO', score: 0.78, fwiRaw: 5 }).kind).toBe('score');
  });

  it('attributes ALTO to the FWI first when both the FWI and the score qualify (backend order)', () => {
    expect(alertTrigger({ ...base, alertLevel: 'ALTO', score: 0.7, fwiRaw: 30 }).kind).toBe('fwi');
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

  it('treats score exactly 0.70 as the score when the FWI is below 20', () => {
    expect(alertTrigger({ ...base, alertLevel: 'ALTO', score: 0.7, fwiRaw: 19.9 }).kind).toBe('score');
    expect(alertTrigger({ ...base, alertLevel: 'ALTO', score: 0.6999, fwiRaw: 20 }).kind).toBe('fwi');
  });

  it('formats the FWI with one decimal and a decimal comma', () => {
    expect(alertTrigger({ ...base, alertLevel: 'ALTO', fwiRaw: 20 }).text).toBe('Activado por FWI 20,0 (peligro meteorológico)');
    expect(alertTrigger({ ...base, alertLevel: 'ALTO', fwiRaw: 20.63 }).text).toBe('Activado por FWI 20,6 (peligro meteorológico)');
  });

  it('attributes CRITICO to the score when it reaches 0.85 and the FWI is below 45', () => {
    expect(alertTrigger({ ...base, alertLevel: 'CRITICO', score: 0.85, fwiRaw: 30, firmsCount: 9 }).kind).toBe('score');
  });

  it('attributes CRITICO to the FWI before the score when both qualify (backend order)', () => {
    expect(alertTrigger({ ...base, alertLevel: 'CRITICO', score: 0.9, fwiRaw: 50 }).kind).toBe('fwi');
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
      text: 'Activado probablemente por focos FIRMS'
    });
    expect(alertTrigger({ ...base, alertLevel: 'CRITICO', score: 0.3, firmsFrpMean: 60 }).kind).toBe('firms');
  });

  it('falls back to a generic FIRMS trigger for CRITICO below the score threshold with no other cause', () => {
    // The client cannot see "FIRMS detection today", the only remaining cause.
    expect(alertTrigger({ ...base, alertLevel: 'CRITICO', score: 0.3, firmsCount: 1 })).toEqual({
      kind: 'firms',
      text: 'Activado probablemente por focos FIRMS'
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

describe('alertTrigger with a backend alertDriver', () => {
  it('uses the backend driver instead of guessing', () => {
    // Client mirror alone would say FWI (45 >= 45); the backend says a FIRMS detection today came first.
    expect(alertTrigger({ ...base, alertLevel: 'CRITICO', score: 0.4, fwiRaw: 50, alertDriver: 'FIRMS_TODAY' })).toEqual({
      kind: 'firms',
      text: 'Activado por un foco FIRMS detectado hoy'
    });
  });

  it('maps every driver to its kind and text', () => {
    const critico = { ...base, alertLevel: 'CRITICO', score: 0.3, fwiRaw: 46.2 };
    expect(alertTrigger({ ...critico, alertDriver: 'SCORE' })).toEqual({ kind: 'score', text: '' });
    expect(alertTrigger({ ...critico, alertDriver: 'FWI' })).toEqual({
      kind: 'fwi',
      text: 'Activado por FWI 46,2 (peligro meteorológico)'
    });
    expect(alertTrigger({ ...critico, alertDriver: 'FIRMS_CLUSTER' })).toEqual({
      kind: 'firms',
      text: 'Activado por varios focos FIRMS recientes'
    });
    expect(alertTrigger({ ...critico, alertDriver: 'FIRMS_FRP' })).toEqual({
      kind: 'firms',
      text: 'Activado por focos FIRMS de alta intensidad'
    });
  });

  it('names the FWI trigger even without a numeric FWI value', () => {
    expect(alertTrigger({ alertLevel: 'ALTO', alertDriver: 'FWI', fwiRaw: null })).toEqual({
      kind: 'fwi',
      text: 'Activado por FWI (peligro meteorológico)'
    });
  });

  it('still reports the score for NORMAL and PREVENTIVO regardless of the driver', () => {
    expect(alertTrigger({ ...base, alertLevel: 'PREVENTIVO', alertDriver: 'SCORE' }).kind).toBe('score');
    expect(alertTrigger({ ...base, alertLevel: 'NORMAL', alertDriver: 'FWI' }).kind).toBe('score');
  });

  it('falls back to the client mirror when the driver is null, missing or unknown (old snapshots)', () => {
    const alto = { ...base, alertLevel: 'ALTO', score: 0.2, fwiRaw: 25 };
    expect(alertTrigger({ ...alto, alertDriver: null }).kind).toBe('fwi');
    expect(alertTrigger({ ...alto }).kind).toBe('fwi');
    expect(alertTrigger({ ...alto, alertDriver: 'SOMETHING_NEW' }).kind).toBe('fwi');
  });
});
