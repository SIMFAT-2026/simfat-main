import { describe, expect, it } from 'vitest';
import {
  levelLabel,
  modeLabel,
  priorityLabel,
  reportCategoryLabel,
  reportStatusLabel,
  resourceCategoryLabel
} from './labels';

describe('levelLabel', () => {
  it('matches the labels used by the territory map', () => {
    expect(levelLabel('NORMAL')).toBe('Normal');
    expect(levelLabel('PREVENTIVO')).toBe('Preventivo');
    expect(levelLabel('ALTO')).toBe('Alto');
    expect(levelLabel('CRITICO')).toBe('Crítico');
  });

  it('covers the legacy alert levels', () => {
    expect(levelLabel('BAJO')).toBe('Bajo');
    expect(levelLabel('MEDIO')).toBe('Medio');
  });

  it('is tolerant of case and missing values', () => {
    expect(levelLabel('critico')).toBe('Crítico');
    expect(levelLabel(undefined)).toBe('');
    expect(levelLabel(null)).toBe('');
  });

  it('humanizes unknown values instead of printing the raw enum', () => {
    expect(levelLabel('MUY_RARO')).toBe('Muy raro');
  });
});

describe('priorityLabel', () => {
  it('uses feminine accented labels', () => {
    expect(priorityLabel('BAJA')).toBe('Baja');
    expect(priorityLabel('MEDIA')).toBe('Media');
    expect(priorityLabel('ALTA')).toBe('Alta');
    expect(priorityLabel('CRITICA')).toBe('Crítica');
  });
});

describe('resourceCategoryLabel', () => {
  it('accents the community resource categories', () => {
    expect(resourceCategoryLabel('GUIA')).toBe('Guía');
    expect(resourceCategoryLabel('CAPACITACION')).toBe('Capacitación');
    expect(resourceCategoryLabel('PROTOCOLO')).toBe('Protocolo');
    expect(resourceCategoryLabel('MATERIAL')).toBe('Material');
  });
});

describe('reportCategoryLabel', () => {
  it('translates citizen report categories', () => {
    expect(reportCategoryLabel('HUMO')).toBe('Humo');
    expect(reportCategoryLabel('IGNICION_POTENCIAL')).toBe('Ignición potencial');
    expect(reportCategoryLabel('INFRAESTRUCTURA_ELECTRICA')).toBe('Infraestructura eléctrica');
    expect(reportCategoryLabel('COMBUSTIBLE_VEGETAL')).toBe('Combustible vegetal');
    expect(reportCategoryLabel('INCENDIO')).toBe('Incendio');
    expect(reportCategoryLabel('OTRO')).toBe('Otro');
  });
});

describe('reportStatusLabel', () => {
  it('capitalizes report statuses', () => {
    expect(reportStatusLabel('RECIBIDO')).toBe('Recibido');
    expect(reportStatusLabel('VALIDADO')).toBe('Validado');
    expect(reportStatusLabel('DERIVADO')).toBe('Derivado');
    expect(reportStatusLabel('DESCARTADO')).toBe('Descartado');
  });

  it('adds the stale marker in lowercase', () => {
    expect(reportStatusLabel('DESCARTADO', { stale: true })).toBe('Descartado (vencido)');
    expect(reportStatusLabel('RECIBIDO', { stale: true })).toBe('Recibido (vencido)');
  });
});

describe('modeLabel', () => {
  it('names the scoring modes in Spanish', () => {
    expect(modeLabel('STANDARD')).toBe('Estándar');
    expect(modeLabel('ENHANCED')).toBe('Con Copernicus');
  });
});
