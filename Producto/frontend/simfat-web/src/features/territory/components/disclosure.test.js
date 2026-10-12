import { describe, expect, it } from 'vitest';
import { nextDisclosureState, shouldCloseOnOutside } from './disclosure';

const node = (children = []) => ({ contains: (target) => children.includes(target) });

describe('shouldCloseOnOutside', () => {
  const inside = {};
  const outside = {};
  const container = node([inside]);

  it('keeps open for clicks inside any container', () => {
    expect(shouldCloseOnOutside(inside, [container])).toBe(false);
    expect(shouldCloseOnOutside(inside, [node(), container])).toBe(false);
  });

  it('closes for clicks outside every container', () => {
    expect(shouldCloseOnOutside(outside, [container, node()])).toBe(true);
  });

  it('ignores missing containers and targets', () => {
    expect(shouldCloseOnOutside(outside, [null, container])).toBe(true);
    expect(shouldCloseOnOutside(null, [container])).toBe(false);
  });
});

describe('nextDisclosureState', () => {
  it('toggles on button activation', () => {
    expect(nextDisclosureState(false, 'toggle')).toEqual({ open: true, restoreFocus: false });
    expect(nextDisclosureState(true, 'toggle')).toEqual({ open: false, restoreFocus: false });
  });

  it('closes on Escape and asks to return focus to the button', () => {
    expect(nextDisclosureState(true, 'escape')).toEqual({ open: false, restoreFocus: true });
  });

  it('ignores Escape while closed and never moves focus on outside clicks', () => {
    expect(nextDisclosureState(false, 'escape')).toEqual({ open: false, restoreFocus: false });
    expect(nextDisclosureState(true, 'outside')).toEqual({ open: false, restoreFocus: false });
  });

  it('keeps the current state for unknown events (e.g. toggling a chip)', () => {
    expect(nextDisclosureState(true, 'chip')).toEqual({ open: true, restoreFocus: false });
  });
});
