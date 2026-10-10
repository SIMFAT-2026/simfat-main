import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react';
import { fetchMapbiomasLayer } from '../services/territoryApiService';
import { mapbiomasCacheKey } from '../utils/mapbiomasLayer';
import { initialMapbiomasState, mapbiomasReducer } from '../utils/mapbiomasState';

// MapBiomas layers are yearly and static between data releases, so a longer TTL
// than the live-layer cache is fine.
const MAPBIOMAS_CACHE_TTL_MS = 600_000;
const layerCache = new Map();

function readCache(key) {
  const entry = layerCache.get(key);
  if (entry && Date.now() < entry.expiresAt) {
    return entry.layer;
  }
  layerCache.delete(key);
  return null;
}

// Loads one MapBiomas indicator on demand: only while `visible`, never as part
// of the region bulk load. Aborts the in-flight request when the year or region
// changes, the layer is hidden, or the component unmounts.
// The selected year is deliberately kept when the region changes: the backend
// default year is computed over all regions, so it is identical everywhere and
// keeping it makes regions directly comparable.
export function useMapbiomasLayer(indicator, { visible, regionId, publicMode }) {
  const [state, dispatch] = useReducer(mapbiomasReducer, initialMapbiomasState);
  const stateRef = useRef(state);
  stateRef.current = state;

  const { selectedYear } = state;
  // Bumping this re-runs the load effect, which retries the same request.
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!visible) {
      return undefined;
    }

    const current = stateRef.current;
    // Already holding exactly this region/year (e.g. right after the backend
    // default year was resolved into an explicit one): nothing to do.
    if (current.layer && current.regionId === regionId && current.layer.year === selectedYear) {
      return undefined;
    }

    dispatch({ type: 'request', regionId });

    const key = mapbiomasCacheKey({ indicator, year: selectedYear, regionId, publicMode });
    const cached = readCache(key);
    if (cached) {
      dispatch({ type: 'success', regionId, requestedYear: selectedYear, layer: cached });
      return undefined;
    }

    const controller = new AbortController();
    fetchMapbiomasLayer(indicator, { regionId, year: selectedYear, publicMode, signal: controller.signal })
      .then((layer) => {
        if (controller.signal.aborted) return;
        if (layer) {
          const expiresAt = Date.now() + MAPBIOMAS_CACHE_TTL_MS;
          layerCache.set(key, { layer, expiresAt });
          // Also cache under the explicit year the backend resolved, so a later
          // explicit request for that year is a hit.
          layerCache.set(mapbiomasCacheKey({ indicator, year: layer.year, regionId, publicMode }), { layer, expiresAt });
        }
        dispatch({ type: 'success', regionId, requestedYear: selectedYear, layer });
      })
      .catch((error) => {
        if (controller.signal.aborted) return;
        // Technical detail stays out of the UI; the reducer sets a fixed message.
        console.warn(`MapBiomas ${indicator} layer failed to load`, error);
        dispatch({ type: 'failure', indicator, regionId, requestedYear: selectedYear });
      });

    return () => controller.abort();
  }, [indicator, visible, regionId, publicMode, selectedYear, attempt]);

  const setYear = useCallback((year) => dispatch({ type: 'select-year', year }), []);
  const retry = useCallback(() => setAttempt((count) => count + 1), []);

  // The effect that requests a new region runs after render, so for one render
  // the state can still describe the previous region: never expose it.
  const matchesRegion = state.regionId === regionId;
  return useMemo(
    () => ({
      selectedYear: state.selectedYear,
      range: state.range,
      defaultYear: state.defaultYear,
      layer: matchesRegion ? state.layer : null,
      stale: matchesRegion && state.stale,
      loading: visible && (!matchesRegion || state.loading),
      error: matchesRegion ? state.error : '',
      setYear,
      retry
    }),
    [state.selectedYear, state.range, state.defaultYear, state.layer, state.stale, state.loading, state.error, matchesRegion, visible, setYear, retry]
  );
}
