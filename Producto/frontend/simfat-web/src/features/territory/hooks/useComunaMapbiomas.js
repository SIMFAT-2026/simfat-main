import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchComunaMapbiomas } from '../services/territoryApiService';
import {
  boundedCacheSet,
  comunaMapbiomasCacheKey,
  readFreshCache,
  staleDataFor
} from '../utils/comunaMapbiomas';

// Small in-memory cache: comuna details are static between data releases.
const CACHE_TTL_MS = 600_000;
const CACHE_MAX_ENTRIES = 50;
const detailCache = new Map();

// Loads the MapBiomas detail of one comuna on demand. Aborts the in-flight
// request when the comuna or year changes or the panel closes; `result.key`
// ties a result to its request so a stale one is never shown (latest wins).
// A fresh cache hit resolves synchronously (no loading flash). While a new year
// loads, the previous data of the SAME comuna stays visible (`stale`).
export function useComunaMapbiomas(comunaId, year) {
  const key = comunaMapbiomasCacheKey(comunaId, year);
  const [result, setResult] = useState({ key: null, comunaId: null, data: null, error: null });
  // Bumping this re-runs the load effect, which retries the same request.
  const [attempt, setAttempt] = useState(0);
  const lastShownRef = useRef(null);

  const cached = comunaId ? readFreshCache(detailCache, key, Date.now()) : null;

  useEffect(() => {
    if (!comunaId) {
      return undefined;
    }
    if (readFreshCache(detailCache, key, Date.now())) {
      return undefined;
    }

    const controller = new AbortController();
    fetchComunaMapbiomas(comunaId, { year, signal: controller.signal })
      .then((data) => {
        if (controller.signal.aborted) return;
        if (data) boundedCacheSet(detailCache, key, { data, expiresAt: Date.now() + CACHE_TTL_MS }, CACHE_MAX_ENTRIES);
        setResult({ key, comunaId, data: data ?? null, error: data ? null : { status: 404 } });
      })
      .catch((error) => {
        if (controller.signal.aborted) return;
        // Technical detail stays out of the UI; the section shows fixed copy.
        console.warn('MapBiomas comuna detail failed to load', error);
        setResult({ key, comunaId, data: null, error: { status: error?.status ?? 0 } });
      });

    return () => controller.abort();
  }, [comunaId, year, key, attempt]);

  const retry = useCallback(() => {
    setResult((previous) => ({ ...previous, key: null, error: null }));
    setAttempt((count) => count + 1);
  }, []);

  if (cached) {
    lastShownRef.current = { comunaId, data: cached };
    return { loading: false, stale: false, data: cached, error: null, retry };
  }
  const current = result.key === key;
  if (current && result.data) {
    lastShownRef.current = { comunaId, data: result.data };
  }
  // Stale data is whatever was last on screen for this comuna (a cache hit or a
  // fetch), never an older result, so the bars do not jump back a year.
  const data = current ? result.data : staleDataFor(lastShownRef.current, comunaId);
  return {
    loading: !current,
    stale: !current && data !== null,
    data,
    error: current ? result.error : null,
    retry
  };
}
