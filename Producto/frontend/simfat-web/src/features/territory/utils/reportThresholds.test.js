import { describe, expect, it } from 'vitest';
import { FWI_CLASS_NOTE, FWI_REFERENCE_CLASSES, fwiClassLabel, fwiClassText, levelRuleRows } from './reportThresholds';

describe('levelRuleRows', () => {
  it('lists the four comuna levels with the thresholds the system uses', () => {
    const rows = levelRuleRows();
    expect(rows.map((r) => r.level)).toEqual(['NORMAL', 'PREVENTIVO', 'ALTO', 'CRITICO']);
    expect(rows[0]).toMatchObject({ label: 'Normal', rule: 'Puntaje 0–49' });
    expect(rows[1]).toMatchObject({ label: 'Preventivo', rule: 'Puntaje 50–69' });
    expect(rows[2]).toMatchObject({ label: 'Alto', rule: 'Puntaje 70–84 o FWI ≥ 20' });
  });

  it('describes every critical trigger', () => {
    const critico = levelRuleRows()[3];
    expect(critico.rule).toBe(
      'Puntaje ≥ 85, FWI ≥ 45, foco FIRMS hoy, ≥ 4 focos o FRP medio ≥ 60 MW'
    );
  });
});

describe('FWI reference classes', () => {
  it('keeps the Canadian class scale and labels it as a reference', () => {
    expect(FWI_CLASS_NOTE).toContain('Clases de referencia del FWI canadiense');
    expect(FWI_REFERENCE_CLASSES.map((c) => c.label)).toEqual([
      'Bajo', 'Moderado', 'Alto', 'Muy alto', 'Extremo',
    ]);
  });

  it('classifies a value by the reference classes', () => {
    expect(fwiClassLabel(5)).toBe('Bajo');
    expect(fwiClassLabel(20.6)).toBe('Moderado');
    expect(fwiClassLabel(25)).toBe('Alto');
    expect(fwiClassLabel(40)).toBe('Muy alto');
    expect(fwiClassLabel(60)).toBe('Extremo');
    expect(fwiClassLabel(null)).toBe('—');
  });
});

describe('fwiClassText', () => {
  it('names the Canadian FWI class so it is not read as the system alert level', () => {
    expect(fwiClassText(20.6)).toBe('Clase FWI canadiense: Moderado');
    expect(fwiClassText(40)).toBe('Clase FWI canadiense: Muy alto');
  });

  it('is empty when the value is not usable', () => {
    expect(fwiClassText(null)).toBe('');
    expect(fwiClassText(NaN)).toBe('');
  });
});
