import { describe, expect, it } from 'vitest';
import {
  chipLabel,
  controlVisible,
  controlYear,
  latestYearLabel,
  latestYearTarget,
  yearAnnouncement,
  yearControlState,
  yearOptions
} from './yearControl';

describe('yearOptions', () => {
  it('lists the years newest first', () => {
    expect(yearOptions(2023, 2025)).toEqual([2025, 2024, 2023]);
  });

  it('returns a single year for a one-year range', () => {
    expect(yearOptions(2024, 2024)).toEqual([2024]);
  });
});

describe('yearAnnouncement', () => {
  it('announces the loaded year once loading is done', () => {
    expect(yearAnnouncement({ label: 'Área quemada', year: 2024, layerYear: 2024, loading: false })).toBe(
      'Área quemada: datos del año 2024 cargados.'
    );
  });

  it('stays silent while loading or without a year', () => {
    expect(yearAnnouncement({ label: 'Área quemada', year: 2024, layerYear: 2024, loading: true })).toBe('');
    expect(
      yearAnnouncement({ label: 'Área quemada', year: undefined, layerYear: undefined, loading: false })
    ).toBe('');
  });

  it('stays silent after a failed or empty load (no layer)', () => {
    expect(yearAnnouncement({ label: 'Área quemada', year: 2024, layerYear: undefined, loading: false })).toBe('');
  });

  it('stays silent when the loaded layer is for another year', () => {
    expect(yearAnnouncement({ label: 'Área quemada', year: 2024, layerYear: 2023, loading: false })).toBe('');
  });
});

describe('yearControlState', () => {
  const range = { minYear: 2013, maxYear: 2025 };

  it('flags the lower bound', () => {
    expect(yearControlState({ year: 2013, range, defaultYear: 2025 })).toEqual({
      atMin: true,
      atMax: false,
      isDefault: false
    });
  });

  it('flags the upper bound and the default year', () => {
    expect(yearControlState({ year: 2025, range, defaultYear: 2025 })).toEqual({
      atMin: false,
      atMax: true,
      isDefault: true
    });
  });

  it('is neither at a bound nor default in the middle of the range', () => {
    expect(yearControlState({ year: 2020, range, defaultYear: 2025 })).toEqual({
      atMin: false,
      atMax: false,
      isDefault: false
    });
  });

  it('is never default when the default year is unknown', () => {
    expect(yearControlState({ year: 2020, range, defaultYear: undefined }).isDefault).toBe(false);
  });
});

describe('latestYearTarget', () => {
  it('targets the known default year explicitly', () => {
    expect(latestYearTarget(2025)).toBe(2025);
  });

  it('falls back to null when the default year is unknown', () => {
    expect(latestYearTarget(undefined)).toBeNull();
  });
});

describe('controlYear', () => {
  it('shows the loaded layer year when it is the selected one', () => {
    expect(controlYear({ layer: { year: 2025 }, selectedYear: 2025, stale: false })).toBe(2025);
  });

  it('shows the selected year, not the displayed layer year, while the layer is stale', () => {
    expect(controlYear({ layer: { year: 2025 }, selectedYear: 2023, stale: true })).toBe(2023);
  });

  it('falls back to the selected year without a layer', () => {
    expect(controlYear({ layer: null, selectedYear: 2023, stale: false })).toBe(2023);
    expect(controlYear({ layer: null, selectedYear: undefined, stale: false })).toBeUndefined();
  });

  it('falls back to the default year when stale and the year was reset', () => {
    expect(controlYear({ layer: { year: 2023 }, selectedYear: undefined, defaultYear: 2025, stale: true })).toBe(2025);
  });
});

describe('latestYearLabel', () => {
  it('names the layer in the accessible label of the latest-year button', () => {
    expect(latestYearLabel('Área quemada')).toBe('Volver al último año de Área quemada');
  });
});

describe('controlVisible', () => {
  it('is hidden when the layer is off or has no state', () => {
    expect(controlVisible({ active: false, state: { range: { minYear: 2020, maxYear: 2024 } } })).toBe(false);
    expect(controlVisible({ active: true, state: undefined })).toBe(false);
  });

  it('is visible with a known range or while the range is loading', () => {
    expect(controlVisible({ active: true, state: { range: { minYear: 2020, maxYear: 2024 }, loading: false } })).toBe(true);
    expect(controlVisible({ active: true, state: { range: null, loading: true } })).toBe(true);
  });

  it('is hidden when the range is unknown and nothing is loading', () => {
    expect(controlVisible({ active: true, state: { range: null, loading: false } })).toBe(false);
  });
});

describe('chipLabel', () => {
  it('drops the year while the inline select shows it', () => {
    expect(
      chipLabel({ plain: 'Cobertura (MapBiomas)', withYear: 'Cobertura (MapBiomas) · 2024', controlVisible: true })
    ).toBe('Cobertura (MapBiomas)');
  });

  it('keeps the year of the displayed layer when no control is visible', () => {
    expect(
      chipLabel({ plain: 'Cobertura (MapBiomas)', withYear: 'Cobertura (MapBiomas) · 2024', controlVisible: false })
    ).toBe('Cobertura (MapBiomas) · 2024');
  });
});
