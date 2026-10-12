import { describe, expect, it } from 'vitest';
import { resolveAccessibleRegions } from './communityAccess';

describe('resolveAccessibleRegions', () => {
  it('gives admins unrestricted access', () => {
    expect(resolveAccessibleRegions(true, ['biobio'])).toBeNull();
  });

  it('keeps the assigned region list of a non-admin', () => {
    const regionIds = ['biobio'];
    expect(resolveAccessibleRegions(false, regionIds)).toBe(regionIds);
  });

  // A fresh [] on every render changed the loader's dependencies each time and
  // re-fetched forever, freezing the page for users without module access.
  it('returns the same empty list on every call when access is missing', () => {
    const first = resolveAccessibleRegions(false, undefined);
    const second = resolveAccessibleRegions(false, null);
    expect(first).toEqual([]);
    expect(second).toBe(first);
  });
});
