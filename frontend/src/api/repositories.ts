import { request } from './http';
import type { CreateRepositoryResponse, RepoFileContent, RepoFiles, Repository } from './types';

const base = '/api/repositories';

export const listRepositories = (signal?: AbortSignal) => request<Repository[]>('repo', base, { signal });

export const getRepository = (id: string, signal?: AbortSignal) =>
  request<Repository>('repo', `${base}/${id}`, { signal });

export const createRepository = (githubUrl: string) =>
  request<CreateRepositoryResponse>('repo', base, { method: 'POST', body: { githubUrl } });

/** Fetch from GitHub, filter and store the files. Synchronous on the server. */
export const ingestRepository = (id: string) => request<Repository>('repo', `${base}/${id}/ingest`, { method: 'POST' });

export const deleteRepository = (id: string) => request<void>('repo', `${base}/${id}`, { method: 'DELETE' });

export const getRepositoryFiles = (id: string, signal?: AbortSignal) =>
  request<RepoFiles>('repo', `${base}/${id}/files`, { signal });

export const getRepositoryContents = (id: string, signal?: AbortSignal) =>
  request<RepoFileContent[]>('repo', `${base}/${id}/contents`, { signal });
