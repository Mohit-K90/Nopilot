const KEY = 'nopilot.token';

export const loadToken = (): string | null => localStorage.getItem(KEY);
export const saveToken = (token: string): void => localStorage.setItem(KEY, token);
export const clearToken = (): void => localStorage.removeItem(KEY);

/** Reads the "exp" claim (seconds) from a JWT without verifying it - only used to avoid pointless requests. */
export function tokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))) as { exp?: number };
    return typeof payload.exp === 'number' && payload.exp * 1000 <= Date.now();
  } catch {
    return true;
  }
}
