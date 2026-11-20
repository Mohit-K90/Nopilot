import { RequireAuth } from './auth/RequireAuth';
import { EmptyState } from './components/ui';
import { AppLayout } from './layouts/AppLayout';
import { AuthLayout } from './layouts/AuthLayout';
import { AccountPage } from './pages/AccountPage';
import { AssistantPage } from './pages/AssistantPage';
import { FilesPage } from './pages/FilesPage';
import { GuidePage } from './pages/GuidePage';
import { LoginPage, RegisterPage } from './pages/AuthPages';
import { OverviewPage } from './pages/OverviewPage';
import { RepoLayout } from './pages/RepoLayout';
import { RepositoriesPage } from './pages/RepositoriesPage';
import { Link, Navigate, Routes, type RouteDef } from './router/Router';
import type { ReactNode } from 'react';

const app = (page: ReactNode) => <RequireAuth><AppLayout>{page}</AppLayout></RequireAuth>;
const repoPage = (id: string, page: ReactNode) => app(<RepoLayout id={id}>{page}</RepoLayout>);

const routes: RouteDef[] = [
  { path: '/', render: () => <Navigate to="/repositories" /> },
  { path: '/login', render: () => <AuthLayout><LoginPage /></AuthLayout> },
  { path: '/register', render: () => <AuthLayout><RegisterPage /></AuthLayout> },
  { path: '/repositories', render: () => app(<RepositoriesPage />) },
  { path: '/account', render: () => app(<AccountPage />) },
  { path: '/repositories/:id', render: ({ id }) => repoPage(id, <OverviewPage />) },
  { path: '/repositories/:id/files', render: ({ id }) => repoPage(id, <FilesPage />) },
  { path: '/repositories/:id/assistant', render: ({ id }) => repoPage(id, <AssistantPage />) },
  { path: '/repositories/:id/guide', render: ({ id }) => repoPage(id, <GuidePage />) },
];

export function App() {
  return (
    <Routes
      routes={routes}
      fallback={app(<EmptyState title="Page not found" action={<Link className="btn" to="/repositories">Go to repositories</Link>}>There is nothing at this address.</EmptyState>)}
    />
  );
}
