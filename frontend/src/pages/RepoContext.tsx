import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { getAiStatus, ingestForAi } from '../api/ai';
import { ApiError, isAbort } from '../api/http';
import { getRepository, ingestRepository } from '../api/repositories';
import type { AiIndexStatus, Repository } from '../api/types';
import { useElapsed } from '../components/ui';
import { dropCachedContents } from '../utils/cache';

export type Busy = 'github' | 'ai' | null;

interface RepoValue {
  repo: Repository;
  /** undefined: unknown (AI service unreachable), null: not indexed yet */
  ai: AiIndexStatus | null | undefined;
  aiError: string | null;
  busy: Busy;
  elapsed: number;
  actionError: string | null;
  runGithub: () => Promise<void>;
  runAi: () => Promise<void>;
  refresh: () => Promise<void>;
}

const RepoContext = createContext<RepoValue | null>(null);

const message = (error: unknown): string => (error instanceof Error ? error.message : String(error));

export function RepoProvider({ id, autostart, children }: { id: string; autostart: boolean; children: (state: {
  value: RepoValue | null; loading: boolean; error: ApiError | Error | null; reload: () => void }) => ReactNode }) {
  const [repo, setRepo] = useState<Repository | null>(null);
  const [ai, setAi] = useState<AiIndexStatus | null | undefined>(undefined);
  const [aiError, setAiError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | Error | null>(null);
  const [busy, setBusy] = useState<Busy>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const alive = useRef(true);
  const elapsed = useElapsed(busy !== null);

  useEffect(() => {
    alive.current = true;
    return () => { alive.current = false; };
  }, []);

  const refresh = useCallback(async () => {
    try {
      const next = await getRepository(id);
      if (!alive.current) return;
      setRepo(next);
      setError(null);
    } catch (e) {
      if (!alive.current || isAbort(e)) return;
      setError(e instanceof Error ? e : new Error(String(e)));
    }
    try {
      const status = await getAiStatus(id);
      if (!alive.current) return;
      setAi(status);
      setAiError(null);
    } catch (e) {
      if (!alive.current) return;
      setAiError(message(e));
    }
    if (alive.current) setLoading(false);
  }, [id]);

  const reload = useCallback(() => { setLoading(true); void refresh(); }, [refresh]);

  useEffect(() => { setRepo(null); setAi(undefined); setLoading(true); void refresh(); }, [id, refresh]);

  // While anything is being processed, poll so the status shown is the one the backend reports.
  const processing = busy !== null || repo?.status === 'INGESTING' || ai?.status === 'INGESTING';
  useEffect(() => {
    if (!processing) return;
    const timer = window.setInterval(() => void refresh(), 3000);
    return () => window.clearInterval(timer);
  }, [processing, refresh]);

  const run = useCallback(async (kind: 'github' | 'ai') => {
    setActionError(null);
    setBusy(kind);
    try {
      if (kind === 'github') {
        const updated = await ingestRepository(id);
        dropCachedContents(id);
        if (alive.current) setRepo(updated);
      } else {
        await ingestForAi(id);
      }
    } catch (e) {
      if (alive.current) setActionError(message(e));
    } finally {
      if (alive.current) setBusy(null);
      await refresh();
    }
  }, [id, refresh]);

  const runGithub = useCallback(() => run('github'), [run]);
  const runAi = useCallback(() => run('ai'), [run]);

  const started = useRef(false);
  useEffect(() => {
    if (autostart && !started.current && repo?.status === 'PENDING') {
      started.current = true;
      window.history.replaceState(null, '', window.location.pathname);
      void runGithub();
    }
  }, [autostart, repo, runGithub]);

  const value: RepoValue | null = repo
    ? { repo, ai, aiError, busy, elapsed, actionError, runGithub, runAi, refresh }
    : null;

  return (
    <RepoContext.Provider value={value}>
      {children({ value, loading, error, reload })}
    </RepoContext.Provider>
  );
}

export function useRepo(): RepoValue {
  const value = useContext(RepoContext);
  if (!value) {
    throw new Error('RepoProvider missing');
  }
  return value;
}
