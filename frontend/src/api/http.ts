/**
 * The only place that talks to the network. Every service call goes through request().
 * To move behind an API Gateway: set VITE_API_URL (or change BASES) - nothing else changes.
 */
import type { ApiErrorBody } from './types';

export type Service = 'user' | 'repo' | 'ai';

const env = import.meta.env;
const clean = (value: string | undefined): string => (value ?? '').replace(/\/+$/, '');
const shared = clean(env.VITE_API_URL);

const BASES: Record<Service, string> = {
  user: clean(env.VITE_USER_API_URL) || shared,
  repo: clean(env.VITE_REPO_API_URL) || shared,
  ai: clean(env.VITE_AI_API_URL) || shared,
};

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }

  get isNetwork(): boolean {
    return this.status === 0;
  }
}

interface HttpConfig {
  getToken: () => string | null;
  onUnauthorized: () => void;
}

let config: HttpConfig = { getToken: () => null, onUnauthorized: () => undefined };

export function configureHttp(next: HttpConfig): void {
  config = next;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'DELETE';
  body?: unknown;
  /** false for login/register (no token yet, and a 401 there is a normal validation failure). */
  auth?: boolean;
  signal?: AbortSignal;
}

export async function request<T>(service: Service, path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true, signal } = options;
  const headers: Record<string, string> = { Accept: 'application/json' };

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (auth) {
    const token = config.getToken();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  let response: Response;
  try {
    response = await fetch(`${BASES[service]}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error;
    }
    throw new ApiError(0, 'Cannot reach the server. Check that the backend services are running.');
  }

  if (response.status === 401 && auth) {
    config.onUnauthorized();
  }
  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function errorMessage(response: Response): Promise<string> {
  const fallback = `Request failed (HTTP ${response.status})`;
  try {
    const text = await response.text();
    if (!text) {
      return response.status === 401 ? 'Authentication required' : fallback;
    }
    const body = JSON.parse(text) as ApiErrorBody;
    return body.message || body.error || fallback;
  } catch {
    return fallback;
  }
}

export function isAbort(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
}
