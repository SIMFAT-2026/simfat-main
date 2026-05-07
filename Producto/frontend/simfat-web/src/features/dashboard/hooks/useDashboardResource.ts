import { useCallback, useEffect, useMemo, useState } from 'react';

interface UseDashboardResourceOptions<T> {
  cacheKey: string;
  fetcher: () => Promise<T>;
  enabled?: boolean;
  ttlMs?: number;
}

interface CacheEntry<T> {
  expiresAt: number;
  data: T;
}

const memoryCache = new Map<string, CacheEntry<unknown>>();
const pendingRequests = new Map<string, Promise<unknown>>();

export interface DashboardResourceState<T> {
  data: T | null;
  loading: boolean;
  error: string;
  reload: () => Promise<void>;
}

function readCache<T>(cacheKey: string): T | null {
  const cached = memoryCache.get(cacheKey) as CacheEntry<T> | undefined;
  if (!cached) {
    return null;
  }

  if (Date.now() > cached.expiresAt) {
    memoryCache.delete(cacheKey);
    return null;
  }

  return cached.data;
}

function writeCache<T>(cacheKey: string, data: T, ttlMs: number): void {
  memoryCache.set(cacheKey, {
    data,
    expiresAt: Date.now() + ttlMs
  });
}

export function useDashboardResource<T>({
  cacheKey,
  fetcher,
  enabled = true,
  ttlMs = 30_000
}: UseDashboardResourceOptions<T>): DashboardResourceState<T> {
  const [data, setData] = useState<T | null>(() => readCache<T>(cacheKey));
  const [loading, setLoading] = useState<boolean>(enabled && !readCache<T>(cacheKey));
  const [error, setError] = useState<string>('');

  const execute = useCallback(
    async (force = false) => {
      if (!enabled) {
        setLoading(false);
        return;
      }

      if (!force) {
        const cached = readCache<T>(cacheKey);
        if (cached !== null) {
          setData(cached);
          setError('');
          setLoading(false);
          return;
        }
      }

      try {
        setLoading(true);
        setError('');

        const existingPromise = pendingRequests.get(cacheKey) as Promise<T> | undefined;
        const requestPromise = existingPromise || fetcher();

        if (!existingPromise) {
          pendingRequests.set(cacheKey, requestPromise);
        }

        const nextData = await requestPromise;
        writeCache(cacheKey, nextData, ttlMs);
        setData(nextData);
      } catch (requestError) {
        const nextError =
          requestError instanceof Error ? requestError.message : 'No fue posible obtener los datos solicitados.';
        setError(nextError);
      } finally {
        pendingRequests.delete(cacheKey);
        setLoading(false);
      }
    },
    [cacheKey, enabled, fetcher, ttlMs]
  );

  useEffect(() => {
    execute(false);
  }, [execute]);

  const reload = useCallback(async () => {
    memoryCache.delete(cacheKey);
    await execute(true);
  }, [cacheKey, execute]);

  return useMemo(
    () => ({
      data,
      loading,
      error,
      reload
    }),
    [data, error, loading, reload]
  );
}
