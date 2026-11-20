// Shapes below mirror the backend DTOs (user-service, repository-service, ai-service).

export interface AuthResponse {
  token: string;
  tokenType?: string;
  message?: string;
}

export interface UserProfile {
  id: string;
  username: string;
  email: string;
}

/** Statuses produced by repository-service (RepositoryStatus enum). */
export type RepoStatus = 'PENDING' | 'INGESTING' | 'READY' | 'FAILED';

export interface Repository {
  id: string;
  githubUrl: string;
  owner: string;
  repoName: string;
  status: RepoStatus;
  defaultBranch: string | null;
  fileCount: number | null;
  totalSizeBytes: number | null;
  skippedFileCount: number | null;
  failureReason: string | null;
  createdAt: string;
  ingestedAt: string | null;
}

export interface CreateRepositoryResponse {
  repositoryId: string;
  status: RepoStatus;
}

export interface RepoFileMeta {
  path: string;
  size: number;
  language: string;
}

export interface RepoFiles {
  repositoryId: string;
  defaultBranch: string | null;
  fileCount: number;
  totalSizeBytes: number;
  files: RepoFileMeta[];
}

/** GET /api/repositories/{id}/contents (no size field: derive it from the content). */
export interface RepoFileContent {
  repositoryId: string;
  path: string;
  language: string;
  content: string;
}

/** Row returned by GET /api/ai/repositories/{id}/status (raw column names). */
export interface AiIndexStatus {
  repository_id: string;
  status: 'INGESTING' | 'READY' | 'FAILED';
  file_count: number;
  chunk_count: number;
  failure_reason: string | null;
  indexed_at: string | null;
}

export interface AiIngestResult {
  repositoryId: string;
  status: string;
  fileCount: number;
  chunkCount: number;
}

export interface ChatSource {
  citation: string;
  filePath: string;
  language: string;
  startLine: number;
  endLine: number;
  score: number;
}

export interface ChatAnswer {
  repositoryId: string;
  answer: string;
  sources: ChatSource[];
}

export interface GuideFacts {
  languages: string[];
  technologies: string[];
  folderStructure: string[];
  entryPoints: string[];
  importantFiles: string[];
  dependencies: string[];
  howToRun: string[];
}

export interface GuideResponse {
  repositoryId: string;
  guide: string;
  facts: GuideFacts;
}

export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
}
