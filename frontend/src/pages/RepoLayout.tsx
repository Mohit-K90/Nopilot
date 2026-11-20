import type { ReactNode } from 'react';
import { Icon } from '../components/Icon';
import { EmptyState, ErrorNotice, Loading, StatusLamp } from '../components/ui';
import { ApiError } from '../api/http';
import { Link, NavLink, useSearchParams } from '../router/Router';
import { githubStage } from '../utils/status';
import { RepoProvider, useRepo } from './RepoContext';

function Header() {
  const { repo, busy } = useRepo();
  const stage = githubStage(repo, busy === 'github');
  return (
    <div className="repohead">
      <div className="label"><Link to="/repositories">Repositories</Link> / {repo.owner}</div>
      <div className="repohead__title">
        <h1>{repo.owner}/{repo.repoName}</h1>
        <StatusLamp lamp={stage.lamp} label={{ idle: 'Pending', busy: 'Fetching', ok: 'Ready', fail: 'Failed' }[stage.lamp]} />
        <a className="btn btn--small btn--ghost" href={repo.githubUrl} target="_blank" rel="noopener noreferrer">
          <Icon name="link" />GitHub
        </a>
      </div>
      <nav className="tabs" aria-label="Repository">
        <NavLink to={`/repositories/${repo.id}`} end><Icon name="overview" />Overview</NavLink>
        <NavLink to={`/repositories/${repo.id}/files`}><Icon name="folder" />Files</NavLink>
        <NavLink to={`/repositories/${repo.id}/assistant`}><Icon name="terminal" />AI Assistant</NavLink>
        <NavLink to={`/repositories/${repo.id}/guide`}><Icon name="book" />Guide</NavLink>
      </nav>
    </div>
  );
}

export function RepoLayout({ id, children }: { id: string; children: ReactNode }) {
  const params = useSearchParams();
  return (
    <RepoProvider id={id} autostart={params.get('start') === 'github'}>
      {({ value, loading, error, reload }) => {
        if (!value) {
          if (loading && !error) return <Loading text="Opening repository" />;
          if (error instanceof ApiError && error.status === 404) {
            return <EmptyState title="Repository not found" action={<Link className="btn" to="/repositories">Back to repositories</Link>}>
              It does not exist, or it belongs to another account.</EmptyState>;
          }
          return <ErrorNotice message={error?.message ?? 'Unknown error'} onRetry={reload} title="Could not open repository" />;
        }
        return (<><Header />{children}</>);
      }}
    </RepoProvider>
  );
}
