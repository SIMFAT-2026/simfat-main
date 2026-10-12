import { describe, expect, it } from 'vitest';
import {
  REPORT_CARD_BACKGROUND,
  REPORT_CARD_COLORS,
  REPORT_DESCRIPTION_PREVIEW_CHARS,
  isLongDescription
} from './reportCard';
import { blendOver, contrastRatio } from './riskPalette';

// The card is rgba(15, 23, 42, 0.93); the lightest thing it can sit on is white.
const WORST_CASE_BACKGROUND = blendOver(REPORT_CARD_BACKGROUND, '#ffffff', 0.93);

describe('REPORT_CARD_COLORS', () => {
  it('covers every report category plus the meta text', () => {
    expect(Object.keys(REPORT_CARD_COLORS.category).sort()).toEqual(['FOCO', 'HUMO', 'INCENDIO', 'OTRO']);
    expect(REPORT_CARD_COLORS.status).toBeTruthy();
    expect(REPORT_CARD_COLORS.time).toBeTruthy();
  });

  it('keeps every text colour at 4.5:1 or more on the dark card', () => {
    const colors = [
      ...Object.values(REPORT_CARD_COLORS.category),
      REPORT_CARD_COLORS.status,
      REPORT_CARD_COLORS.time
    ];
    for (const color of colors) {
      expect(contrastRatio(color, WORST_CASE_BACKGROUND)).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('keeps the active fire badge visibly red', () => {
    const hex = REPORT_CARD_COLORS.category.INCENDIO;
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    expect(r).toBeGreaterThan(g * 1.5);
  });
});

describe('isLongDescription', () => {
  it('flags text that would be cut by the two-line preview', () => {
    expect(isLongDescription('x'.repeat(REPORT_DESCRIPTION_PREVIEW_CHARS + 1))).toBe(true);
  });

  it('leaves short or empty text alone', () => {
    expect(isLongDescription('Humo cerca del camino')).toBe(false);
    expect(isLongDescription('')).toBe(false);
    expect(isLongDescription(null)).toBe(false);
    expect(isLongDescription(undefined)).toBe(false);
  });

  it('ignores surrounding whitespace', () => {
    expect(isLongDescription(`  ${'x'.repeat(REPORT_DESCRIPTION_PREVIEW_CHARS)}  `)).toBe(false);
  });
});
