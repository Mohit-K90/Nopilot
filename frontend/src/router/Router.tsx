/**
 * A deliberately small History-API router (about 100 lines) instead of a dependency.
 * Supports: route table with :params, Link/NavLink, navigate(), query string access.
 */
import {
  createContext, useCallback, useContext, useEffect, useMemo, useState,
  type AnchorHTMLAttributes, type MouseEvent, type ReactNode,
} from 'react';

interface Location {
  path: string;
  search: string;
}

interface RouterValue extends Location {
  navigate: (to: string, options?: { replace?: boolean }) => void;
}

const RouterContext = createContext<RouterValue | null>(null);

const read = (): Location => ({ path: window.location.pathname, search: window.location.search });

export function Router({ children }: { children: ReactNode }) {
  const [location, setLocation] = useState<Location>(read);

  useEffect(() => {
    const onPop = () => setLocation(read());
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, []);

  const navigate = useCallback((to: string, options?: { replace?: boolean }) => {
    if (options?.replace) {
      window.history.replaceState(null, '', to);
    } else {
      window.history.pushState(null, '', to);
    }
    setLocation(read());
    window.scrollTo(0, 0);
  }, []);

  const value = useMemo(() => ({ ...location, navigate }), [location, navigate]);
  return <RouterContext.Provider value={value}>{children}</RouterContext.Provider>;
}

function useRouter(): RouterValue {
  const value = useContext(RouterContext);
  if (!value) {
    throw new Error('Router missing');
  }
  return value;
}

export const useNavigate = () => useRouter().navigate;

export function useSearchParams(): URLSearchParams {
  const { search } = useRouter();
  return useMemo(() => new URLSearchParams(search), [search]);
}

export function usePath(): string {
  return useRouter().path;
}

export type Params = Record<string, string>;

export function matchPath(pattern: string, path: string): Params | null {
  const a = pattern.split('/').filter(Boolean);
  const b = path.split('/').filter(Boolean);
  if (a.length !== b.length) {
    return null;
  }
  const params: Params = {};
  for (let i = 0; i < a.length; i++) {
    if (a[i].startsWith(':')) {
      params[a[i].slice(1)] = decodeURIComponent(b[i]);
    } else if (a[i] !== b[i]) {
      return null;
    }
  }
  return params;
}

export interface RouteDef {
  path: string;
  render: (params: Params) => ReactNode;
}

export function Routes({ routes, fallback }: { routes: RouteDef[]; fallback: ReactNode }) {
  const { path } = useRouter();
  for (const route of routes) {
    const params = matchPath(route.path, path);
    if (params) {
      return <>{route.render(params)}</>;
    }
  }
  return <>{fallback}</>;
}

interface LinkProps extends Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'href'> {
  to: string;
}

export function Link({ to, onClick, children, ...rest }: LinkProps) {
  const navigate = useNavigate();
  const handle = (event: MouseEvent<HTMLAnchorElement>) => {
    onClick?.(event);
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || rest.target === '_blank') {
      return;
    }
    event.preventDefault();
    navigate(to);
  };
  return (
    <a href={to} onClick={handle} {...rest}>
      {children}
    </a>
  );
}

/** Link that gets aria-current="page" (styled as active) when its path matches. */
export function NavLink({ to, end = false, className = '', ...rest }: LinkProps & { end?: boolean }) {
  const path = usePath();
  const target = to.split('?')[0];
  const active = end ? path === target : path === target || path.startsWith(`${target}/`);
  return <Link to={to} aria-current={active ? 'page' : undefined} className={className} {...rest} />;
}

export function Navigate({ to }: { to: string }) {
  const navigate = useNavigate();
  useEffect(() => navigate(to, { replace: true }), [navigate, to]);
  return null;
}
