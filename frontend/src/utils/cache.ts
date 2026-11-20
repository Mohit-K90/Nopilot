import type { RepoFileContent } from '../api/types';

/** In-memory cache of repository contents (can be several MB; fetched once per repository). */
const contents = new Map<string, RepoFileContent[]>();

export const getCachedContents = (id: string) => contents.get(id);
export const setCachedContents = (id: string, files: RepoFileContent[]) => contents.set(id, files);
export const dropCachedContents = (id: string) => contents.delete(id);

const PREFIXES = ['nopilot.guide.', 'nopilot.chat.'];

/** Removes per-repository data persisted in the browser (used on logout / repository deletion). */
export function clearRepositoryCaches(): void {
  contents.clear();
  for (const store of [localStorage, sessionStorage]) {
    for (const key of Object.keys(store)) {
      if (PREFIXES.some((prefix) => key.startsWith(prefix))) {
        store.removeItem(key);
      }
    }
  }
}

export function clearRepositoryData(id: string): void {
  dropCachedContents(id);
  localStorage.removeItem(`nopilot.guide.${id}`);
  sessionStorage.removeItem(`nopilot.chat.${id}`);
}
