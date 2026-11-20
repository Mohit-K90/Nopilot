import { ApiError, request } from './http';
import type { AiIndexStatus, AiIngestResult, ChatAnswer, GuideResponse } from './types';

const base = (id: string) => `/api/ai/repositories/${id}`;

/** null means "this repository has not been indexed by the AI service yet" (HTTP 404). */
export async function getAiStatus(id: string, signal?: AbortSignal): Promise<AiIndexStatus | null> {
  try {
    return await request<AiIndexStatus>('ai', `${base(id)}/status`, { signal });
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

/** Chunk, embed and store the repository. Synchronous on the server (can take minutes). */
export const ingestForAi = (id: string, signal?: AbortSignal) =>
  request<AiIngestResult>('ai', `${base(id)}/ingest`, { method: 'POST', signal });

export const askQuestion = (id: string, question: string, signal?: AbortSignal) =>
  request<ChatAnswer>('ai', `${base(id)}/chat`, { method: 'POST', body: { question }, signal });

/** Runs the local LLM every time it is called: callers should cache the result. */
export const generateGuide = (id: string, signal?: AbortSignal) =>
  request<GuideResponse>('ai', `${base(id)}/guide`, { signal });
