import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, isAbort } from '../api/http';

export interface AsyncState<T> {
  data: T | undefined;
  error: ApiError | Error | undefined;
  loading: boolean;
  reload: () => void;
}

/** Runs an abortable async function when deps change; ignores stale results. */
export function useAsync<T>(fn: (signal: AbortSignal) => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<ApiError | Error>();
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  const fnRef = useRef(fn);
  fnRef.current = fn;

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    fnRef
      .current(controller.signal)
      .then((result) => {
        setData(result);
        setLoading(false);
      })
      .catch((e: unknown) => {
        if (isAbort(e)) {
          return;
        }
        setError(e instanceof Error ? e : new Error(String(e)));
        setLoading(false);
      });
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  const reload = useCallback(() => setTick((n) => n + 1), []);
  return { data, error, loading, reload };
}
