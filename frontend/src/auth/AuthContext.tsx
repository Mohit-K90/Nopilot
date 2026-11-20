import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import * as authApi from '../api/auth';
import { configureHttp, isAbort } from '../api/http';
import type { UserProfile } from '../api/types';
import { clearRepositoryCaches } from '../utils/cache';
import { clearToken, loadToken, saveToken, tokenExpired } from './token';

type Status = 'loading' | 'anonymous' | 'authenticated';

interface AuthValue {
  status: Status;
  user: UserProfile | null;
  /** Shown once on the login screen after an automatic sign-out. */
  notice: string | null;
  login: (email: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  clearNotice: () => void;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('loading');
  const [user, setUser] = useState<UserProfile | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const tokenRef = useRef<string | null>(loadToken());

  const endSession = useCallback((message: string | null) => {
    tokenRef.current = null;
    clearToken();
    clearRepositoryCaches();
    setUser(null);
    setStatus('anonymous');
    setNotice(message);
  }, []);

  useEffect(() => {
    configureHttp({
      getToken: () => tokenRef.current,
      onUnauthorized: () => {
        if (tokenRef.current) {
          endSession('Your session has expired. Please sign in again.');
        }
      },
    });
  }, [endSession]);

  // Restore the session on page load: validate the stored token against the profile endpoint.
  useEffect(() => {
    const token = tokenRef.current;
    if (!token || tokenExpired(token)) {
      if (token) {
        endSession('Your session has expired. Please sign in again.');
      } else {
        setStatus('anonymous');
      }
      return;
    }
    const controller = new AbortController();
    authApi
      .fetchProfile(controller.signal)
      .then((profile) => {
        setUser(profile);
        setStatus('authenticated');
      })
      .catch((error: unknown) => {
        if (isAbort(error)) {
          return;
        }
        // 401 is handled by onUnauthorized; for network errors keep the user out but keep the token.
        setStatus('anonymous');
      });
    return () => controller.abort();
  }, [endSession]);

  const startSession = useCallback(async (token: string) => {
    tokenRef.current = token;
    saveToken(token);
    const profile = await authApi.fetchProfile();
    setUser(profile);
    setNotice(null);
    setStatus('authenticated');
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const response = await authApi.login(email, password);
      await startSession(response.token);
    },
    [startSession],
  );

  const register = useCallback(
    async (username: string, email: string, password: string) => {
      const response = await authApi.register(username, email, password);
      // register returns a token as well; fall back to an explicit login if it does not.
      const token = response.token ?? (await authApi.login(email, password)).token;
      await startSession(token);
    },
    [startSession],
  );

  const value = useMemo<AuthValue>(
    () => ({ status, user, notice, login, register, logout: () => endSession(null), clearNotice: () => setNotice(null) }),
    [status, user, notice, login, register, endSession],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('AuthProvider missing');
  }
  return value;
}
