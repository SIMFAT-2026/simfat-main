import { describe, expect, it } from 'vitest';
import {
  YEAR_PAGE_STEP,
  clampYear,
  stepYear,
  yearAnnouncement,
  yearControlLabel,
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
    expect(yearAnnouncement({ label: 'Área quemada', year: 2024, loading: false })).toBe(
      'Área quemada: datos del año 2024 cargados.'
    );
  });

  it('stays silent while loading or without a year', () => {
    expect(yearAnnouncement({ label: 'Área quemada', year: 2024, loading: true })).toBe('');
    expect(yearAnnouncement({ label: 'Área quemada', year: undefined, loading: false })).toBe('');
  });
});
