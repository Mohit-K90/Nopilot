import type { ReactNode } from 'react';
import { Loading } from '../components/ui';
import { Navigate, usePath } from '../router/Router';
import { useAuth } from './AuthContext';

export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const path = usePath();
  if (status === 'loading') {
    return <div style={{ maxWidth: 360, margin: '20vh auto' }}><Loading text="Restoring session" /></div>;
  }
  if (status === 'anonymous') {
    return <Navigate to={`/login?next=${encodeURIComponent(path + window.location.search)}`} />;
  }
  return <>{children}</>;
}
