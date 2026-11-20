import { request } from './http';
import type { AuthResponse, UserProfile } from './types';

export const login = (email: string, password: string) =>
  request<AuthResponse>('user', '/api/auth/login', { method: 'POST', body: { email, password }, auth: false });

export const register = (username: string, email: string, password: string) =>
  request<AuthResponse>('user', '/api/auth/register', {
    method: 'POST',
    body: { username, email, password },
    auth: false,
  });

export const fetchProfile = (signal?: AbortSignal) => request<UserProfile>('user', '/api/users/me', { signal });
