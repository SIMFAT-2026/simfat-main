import { describe, expect, it } from 'vitest';
import {
  YEAR_PAGE_STEP,
  clampYear,
  latestYearTarget,
  stepYear,
  yearAnnouncement,
  yearControlLabel,
  yearControlState,
  yearKeyAction,
  yearOptions
} from './yearControl';

describe('clampYear', () => {
  it('keeps years inside the range and clamps the rest', () => {
    expect(clampYear(2020, 2013, 2025)).toBe(2020);
    expect(clampYear(2010, 2013, 2025)).toBe(2013);
    expect(clampYear(2030, 2013, 2025)).toBe(2025);
  });

  it('returns the lower bound for a non-integer year', () => {
    expect(clampYear(Number.NaN, 2013, 2025)).toBe(2013);
  });
});

describe('stepYear', () => {
  it('moves one year with the arrow keys', () => {
    expect(stepYear(2020, 'ArrowRight', 2013, 2025)).toBe(2021);
    expect(stepYear(2020, 'ArrowUp', 2013, 2025)).toBe(2021);
    expect(stepYear(2020, 'ArrowLeft', 2013, 2025)).toBe(2019);
    expect(stepYear(2020, 'ArrowDown', 2013, 2025)).toBe(2019);
  });

  it('jumps to the bounds with Home and End', () => {
    expect(stepYear(2020, 'Home', 2013, 2025)).toBe(2013);
    expect(stepYear(2020, 'End', 2013, 2025)).toBe(2025);
  });

  it('moves a page with PageUp and PageDown, clamped to the range', () => {
    expect(YEAR_PAGE_STEP).toBe(5);
    expect(stepYear(2020, 'PageUp', 2013, 2025)).toBe(2025);
    expect(stepYear(2018, 'PageUp', 2013, 2025)).toBe(2023);
    expect(stepYear(2020, 'PageDown', 2013, 2025)).toBe(2015);
    expect(stepYear(2015, 'PageDown', 2013, 2025)).toBe(2013);
  });

  it('never leaves the range with the arrows', () => {
    expect(stepYear(2025, 'ArrowRight', 2013, 2025)).toBe(2025);
    expect(stepYear(2013, 'ArrowLeft', 2013, 2025)).toBe(2013);
  });

  it('returns null for keys it does not handle', () => {
    expect(stepYear(2020, 'a', 2013, 2025)).toBeNull();
    expect(stepYear(2020, 'Enter', 2013, 2025)).toBeNull();
  });
});

describe('yearOptions', () => {
  it('lists the years newest first', () => {
    expect(yearOptions(2023, 2025)).toEqual([2025, 2024, 2023]);
  });

  it('returns a single year for a one-year range', () => {
    expect(yearOptions(2024, 2024)).toEqual([2024]);
  });
});

describe('yearControlLabel', () => {
  it('combines the layer name with the year', () => {
    expect(yearControlLabel('Área quemada', 2025)).toBe('Área quemada · Año: 2025');
  });

  it('shows a placeholder while the year is unknown', () => {
    expect(yearControlLabel('Área quemada', undefined)).toBe('Área quemada · Año: …');
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

  it('uses the pending draft over the loaded year', () => {
    const state = yearControlState({ draft: 2013, loadedYear: 2020, range, defaultYear: 2025 });
    expect(state).toEqual({ current: 2013, atMin: true, atMax: false, isDefault: false });
  });

  it('falls back to the loaded year without a draft', () => {
    const state = yearControlState({ draft: undefined, loadedYear: 2025, range, defaultYear: 2025 });
    expect(state).toEqual({ current: 2025, atMin: false, atMax: true, isDefault: true });
  });

  it('bases isDefault on the loaded year, not the draft', () => {
    expect(yearControlState({ draft: 2020, loadedYear: 2025, range, defaultYear: 2025 }).isDefault).toBe(true);
    expect(yearControlState({ draft: 2025, loadedYear: 2020, range, defaultYear: 2025 }).isDefault).toBe(false);
  });

  it('is never default when the default year is unknown', () => {
    expect(yearControlState({ draft: 2020, loadedYear: 2020, range, defaultYear: undefined }).isDefault).toBe(false);
  });
});

describe('yearKeyAction', () => {
  it('maps a plain key to the next year', () => {
    expect(yearKeyAction({ key: 'ArrowRight' }, 2020, 2013, 2025)).toBe(2021);
  });

  it('ignores keys when a modifier is held', () => {
    for (const modifier of ['altKey', 'ctrlKey', 'metaKey', 'shiftKey']) {
      expect(yearKeyAction({ key: 'ArrowRight', [modifier]: true }, 2020, 2013, 2025)).toBeNull();
    }
  });

  it('returns null for keys it does not handle', () => {
    expect(yearKeyAction({ key: 'Tab' }, 2020, 2013, 2025)).toBeNull();
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
