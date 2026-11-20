import type { ReactNode } from 'react';
import { useAuth } from '../auth/AuthContext';
import { Icon } from '../components/Icon';
import { Button } from '../components/ui';
import { Link, NavLink } from '../router/Router';

export function AppLayout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  return (
    <div className="shell">
      <header className="topbar">
        <Link to="/repositories" className="brand">
          <span className="brand__name">NoPilot</span>
          <span className="brand__tag">Understand with AI. Engineer without it.</span>
        </Link>
        <span className="spacer" />
        {user && (
          <div className="userchip"><strong>{user.username}</strong>{user.email}</div>
        )}
        <Button small icon="power" onClick={logout} title="Sign out">Sign out</Button>
      </header>
      <div className="body">
        <nav className="rail" aria-label="Main">
          <div className="rail__label label">Workspace</div>
          <NavLink to="/repositories"><Icon name="database" />Repositories</NavLink>
          <NavLink to="/account" end><Icon name="user" />Account</NavLink>
        </nav>
        <main className="main">{children}</main>
      </div>
      <footer className="statusbar">
        <span>NoPilot workspace</span>
        <span>·</span>
        <span>{user ? `session: ${user.email}` : 'no session'}</span>
      </footer>
    </div>
  );
}
