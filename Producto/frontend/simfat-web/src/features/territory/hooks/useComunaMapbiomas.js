import { useCallback, useEffect, useState } from 'react';
import { fetchComunaMapbiomas } from '../services/territoryApiService';
import { comunaMapbiomasCacheKey } from '../utils/comunaMapbiomas';

// Small in-memory cache: comuna details are static between data releases.
const CACHE_TTL_MS = 600_000;
const detailCache = new Map();

function readCache(key) {
  const entry = detailCache.get(key);
  if (entry && Date.now() < entry.expiresAt) {
    return entry.data;
  }
  detailCache.delete(key);
  return null;
}

// Loads the MapBiomas detail of one comuna on demand. Aborts the in-flight
// request when the comuna or year changes or the panel closes; `result.key`
// ties a result to its request so a stale one is never shown (latest wins).
export function useComunaMapbiomas(comunaId, year) {
  const key = comunaMapbiomasCacheKey(comunaId, year);
  const [result, setResult] = useState({ key: null, data: null, error: null });
  // Bumping this re-runs the load effect, which retries the same request.
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!comunaId) {
      return undefined;
    }
    const cached = readCache(key);
    if (cached) {
      setResult({ key, data: cached, error: null });
      return undefined;
    }

    const controller = new AbortController();
    fetchComunaMapbiomas(comunaId, { year, signal: controller.signal })
      .then((data) => {
        if (controller.signal.aborted) return;
        if (data) detailCache.set(key, { data, expiresAt: Date.now() + CACHE_TTL_MS });
        setResult({ key, data: data ?? null, error: data ? null : { status: 404 } });
      })
      .catch((error) => {
        if (controller.signal.aborted) return;
        // Technical detail stays out of the UI; the section shows fixed copy.
        console.warn('MapBiomas comuna detail failed to load', error);
        setResult({ key, data: null, error: { status: error?.status ?? 0 } });
      });

    return () => controller.abort();
  }, [comunaId, year, key, attempt]);

  const retry = useCallback(() => {
    setResult({ key: null, data: null, error: null });
    setAttempt((count) => count + 1);
  }, []);

  const current = result.key === key;
  return {
    loading: !current,
    data: current ? result.data : null,
    error: current ? result.error : null,
    retry
  };
}
