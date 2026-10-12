import { describe, expect, it } from 'vitest';
import { compassLabelFrom } from './compass';

describe('compassLabelFrom', () => {
  it('uses Spanish cardinal abbreviations (O for oeste, not W)', () => {
    expect(compassLabelFrom(0)).toBe('N');
    expect(compassLabelFrom(90)).toBe('E');
    expect(compassLabelFrom(180)).toBe('S');
    expect(compassLabelFrom(225)).toBe('SO');
    expect(compassLabelFrom(270)).toBe('O');
    expect(compassLabelFrom(315)).toBe('NO');
  });

  it('wraps bearings near 360 back to north', () => {
    expect(compassLabelFrom(350)).toBe('N');
    expect(compassLabelFrom(360)).toBe('N');
  });
});
