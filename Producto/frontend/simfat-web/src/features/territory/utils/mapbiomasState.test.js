import { describe, expect, it } from 'vitest';
import { initialMapbiomasState, mapbiomasReducer } from './mapbiomasState';

const layer2025 = { indicator: 'BURNED', year: 2025, values: {} };
const layer2023 = { indicator: 'BURNED', year: 2023, values: {} };

function run(actions, start = initialMapbiomasState) {
  return actions.reduce(mapbiomasReducer, start);
}

describe('mapbiomasReducer', () => {
  it('starts idle with no year chosen', () => {
    expect(initialMapbiomasState).toEqual({
      selectedYear: undefined,
      regionId: null,
      layer: null,
      loading: false,
      error: ''
    });
  });

  it('marks a request as loading for its region', () => {
    const state = run([{ type: 'request', regionId: 'biobio' }]);
    expect(state.loading).toBe(true);
    expect(state.regionId).toBe('biobio');
    expect(state.layer).toBeNull();
    expect(state.error).toBe('');
  });

  it('keeps the explicit year the backend resolved after the first load', () => {
    const state = run([
      { type: 'request', regionId: 'biobio' },
      { type: 'success', regionId: 'biobio', requestedYear: undefined, layer: layer2025 }
    ]);
    expect(state.layer).toBe(layer2025);
    expect(state.selectedYear).toBe(2025);
    expect(state.loading).toBe(false);
  });

  it('drops the previous layer when the year changes so it is never shown under another year', () => {
    const loaded = run([
      { type: 'request', regionId: 'biobio' },
      { type: 'success', regionId: 'biobio', requestedYear: undefined, layer: layer2025 },
      { type: 'select-year', year: 2023 },
      { type: 'request', regionId: 'biobio' }
    ]);
    expect(loaded.selectedYear).toBe(2023);
    expect(loaded.layer).toBeNull();
    expect(loaded.loading).toBe(true);
  });

  it('keeps the loaded layer when the same region and year are requested again', () => {
    const state = run([
      { type: 'request', regionId: 'biobio' },
      { type: 'success', regionId: 'biobio', requestedYear: undefined, layer: layer2025 },
      { type: 'request', regionId: 'biobio' }
    ]);
    expect(state.layer).toBe(layer2025);
  });

  it('drops the layer when the region changes but keeps the chosen year', () => {
    const state = run([
      { type: 'request', regionId: 'biobio' },
      { type: 'success', regionId: 'biobio', requestedYear: undefined, layer: layer2025 },
      { type: 'request', regionId: 'nuble' }
    ]);
    expect(state.layer).toBeNull();
    expect(state.selectedYear).toBe(2025);
    expect(state.regionId).toBe('nuble');
  });

  it('ignores a success for a stale region or year', () => {
    const base = run([
      { type: 'select-year', year: 2023 },
      { type: 'request', regionId: 'nuble' }
    ]);
    expect(mapbiomasReducer(base, { type: 'success', regionId: 'biobio', requestedYear: 2023, layer: layer2023 })).toBe(base);
    expect(mapbiomasReducer(base, { type: 'success', regionId: 'nuble', requestedYear: 2025, layer: layer2025 })).toBe(base);
  });

  it('turns a missing layer into an error', () => {
    const state = run([
      { type: 'request', regionId: 'biobio' },
      { type: 'success', regionId: 'biobio', requestedYear: undefined, layer: null }
    ]);
    expect(state.layer).toBeNull();
    expect(state.loading).toBe(false);
    expect(state.error).not.toBe('');
  });

  it('records a failure for the current request and ignores stale ones', () => {
    const requested = run([{ type: 'request', regionId: 'biobio' }]);
    const failed = mapbiomasReducer(requested, {
      type: 'failure',
      regionId: 'biobio',
      requestedYear: undefined,
      message: 'boom'
    });
    expect(failed.error).toBe('boom');
    expect(failed.loading).toBe(false);
    expect(failed.layer).toBeNull();

    const stale = mapbiomasReducer(requested, {
      type: 'failure',
      regionId: 'nuble',
      requestedYear: undefined,
      message: 'boom'
    });
    expect(stale).toBe(requested);
  });

  it('select-year accepts integers and null (back to the backend default)', () => {
    expect(run([{ type: 'select-year', year: 2019 }]).selectedYear).toBe(2019);
    expect(run([{ type: 'select-year', year: 2019 }, { type: 'select-year', year: null }]).selectedYear).toBeUndefined();
  });

  it('select-year ignores invalid years', () => {
    const state = run([{ type: 'select-year', year: 2019 }]);
    expect(mapbiomasReducer(state, { type: 'select-year', year: 'abc' })).toBe(state);
    expect(mapbiomasReducer(state, { type: 'select-year', year: 2019.5 })).toBe(state);
    expect(mapbiomasReducer(state, { type: 'select-year', year: 2019 })).toBe(state);
  });

  it('returns the same state for unknown actions', () => {
    expect(mapbiomasReducer(initialMapbiomasState, { type: 'nope' })).toBe(initialMapbiomasState);
  });
});
