import type { AiIndexStatus, RepoStatus, Repository } from '../api/types';

export type Lamp = 'idle' | 'busy' | 'ok' | 'fail';

export interface StageView {
  label: string;
  lamp: Lamp;
  text: string;
  detail?: string;
}

/**
 * Maps the real backend states onto the two-stage pipeline shown in the UI:
 *   1. repository-service: GitHub fetch + filtering  (PENDING / INGESTING / READY / FAILED)
 *   2. ai-service: chunking + embeddings             (no index / INGESTING / READY / FAILED)
 * Nothing here is invented: every label is derived from a status the API returned.
 */
export function githubStage(repo: Repository, running: boolean): StageView {
  const status: RepoStatus = running ? 'INGESTING' : repo.status;
  switch (status) {
    case 'PENDING':
      return { label: 'GitHub fetch', lamp: 'idle', text: 'Registered, not fetched yet' };
    case 'INGESTING':
      return { label: 'GitHub fetch', lamp: 'busy', text: 'Fetching and filtering files from GitHub' };
    case 'READY':
      return {
        label: 'GitHub fetch',
        lamp: 'ok',
        text: `${repo.fileCount ?? 0} files stored`,
        detail: repo.skippedFileCount ? `${repo.skippedFileCount} relevant files skipped (size/limits)` : undefined,
      };
    case 'FAILED':
      return { label: 'GitHub fetch', lamp: 'fail', text: 'Fetch failed', detail: repo.failureReason ?? undefined };
  }
}

export function aiStage(repo: Repository, ai: AiIndexStatus | null | undefined, running: boolean): StageView {
  const label = 'AI index';
  if (repo.status !== 'READY') {
    return { label, lamp: 'idle', text: 'Waiting for the GitHub fetch to finish' };
  }
  if (running || ai?.status === 'INGESTING') {
    return { label, lamp: 'busy', text: 'Generating embeddings (chunking and indexing files)' };
  }
  if (!ai) {
    return { label, lamp: 'idle', text: 'Not indexed for AI yet' };
  }
  if (ai.status === 'READY') {
    return { label, lamp: 'ok', text: `${ai.file_count} files, ${ai.chunk_count} chunks indexed` };
  }
  return { label, lamp: 'fail', text: 'Indexing failed', detail: ai.failure_reason ?? undefined };
}

/** Single headline status for lists. */
export function overallStatus(repo: Repository): { lamp: Lamp; text: string } {
  const stage = githubStage(repo, false);
  return { lamp: stage.lamp, text: { PENDING: 'Pending', INGESTING: 'Fetching', READY: 'Ready', FAILED: 'Failed' }[repo.status] };
}
