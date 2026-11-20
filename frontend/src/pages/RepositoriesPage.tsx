import { useEffect, useState, type FormEvent } from 'react';
import { createRepository, deleteRepository, listRepositories } from '../api/repositories';
import type { Repository } from '../api/types';
import { Button, EmptyState, ErrorNotice, Field, Loading, Panel, StatusLamp } from '../components/ui';
import { useAsync } from '../hooks/useAsync';
import { Link, useNavigate } from '../router/Router';
import { clearRepositoryData } from '../utils/cache';
import { formatDate, GITHUB_URL, normalizeGithubUrl, plural } from '../utils/format';
import { overallStatus } from '../utils/status';

function ConnectForm() {
  const navigate = useNavigate();
  const [url, setUrl] = useState('');
  const [fetchNow, setFetchNow] = useState(true);
  const [error, setError] = useState<string | undefined>();
  const [serverError, setServerError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const normalized = normalizeGithubUrl(url);
    if (!GITHUB_URL.test(normalized)) {
      setError('Use the form https://github.com/owner/repository');
      return;
    }
    setError(undefined);
    setServerError(null);
    setBusy(true);
    try {
      const created = await createRepository(normalized);
      navigate(`/repositories/${created.repositoryId}${fetchNow ? '?start=github' : ''}`);
    } catch (e) {
      setServerError(e instanceof Error ? e.message : String(e));
      setBusy(false);
    }
  };

  return (
    <Panel title="Connect repository">
      <form onSubmit={submit} noValidate>
        <Field label="GitHub URL" mono placeholder="https://github.com/owner/repo" value={url} error={error}
          hint="Public repositories only." onChange={(e) => setUrl(e.target.value)} />
        <label className="row" style={{ marginBottom: 16, gap: 8, cursor: 'pointer' }}>
          <input type="checkbox" checked={fetchNow} onChange={(e) => setFetchNow(e.target.checked)} />
          <span>Fetch files from GitHub right away</span>
        </label>
        {serverError && <div style={{ marginBottom: 16 }}><ErrorNotice title="Could not connect" message={serverError} /></div>}
        <Button type="submit" variant="primary" icon="plus" busy={busy}>{busy ? 'Connecting' : 'Connect'}</Button>
      </form>
    </Panel>
  );
}

function RepoRow({ repo, onDeleted }: { repo: Repository; onDeleted: () => void }) {
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const status = overallStatus(repo);

  const remove = async () => {
    setBusy(true);
    setError(null);
    try {
      await deleteRepository(repo.id);
      clearRepositoryData(repo.id);
      onDeleted();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setBusy(false);
      setConfirming(false);
    }
  };

  return (
    <tr>
      <td>
        <Link to={`/repositories/${repo.id}`} className="name">{repo.owner}/{repo.repoName}</Link>
        <div className="muted" style={{ fontSize: 12 }}>{repo.githubUrl.replace('https://', '')}</div>
        {error && <div className="field__error" role="alert">{error}</div>}
      </td>
      <td><StatusLamp lamp={status.lamp} label={status.text} /></td>
      <td className="hide-sm mono">{repo.defaultBranch ?? '—'}</td>
      <td className="num">{repo.fileCount ?? '—'}</td>
      <td className="hide-sm">{formatDate(repo.createdAt)}</td>
      <td style={{ whiteSpace: 'nowrap', textAlign: 'right' }}>
        {confirming ? (
          <span className="row" style={{ justifyContent: 'flex-end', gap: 6 }}>
            <Button small variant="danger" busy={busy} onClick={remove}>Confirm delete</Button>
            <Button small onClick={() => setConfirming(false)}>Cancel</Button>
          </span>
        ) : (
          <span className="row" style={{ justifyContent: 'flex-end', gap: 6 }}>
            <Link className="btn btn--small" to={`/repositories/${repo.id}`}>Open</Link>
            <Button small icon="trash" aria-label={`Delete ${repo.repoName}`} onClick={() => setConfirming(true)} />
          </span>
        )}
      </td>
    </tr>
  );
}

export function RepositoriesPage() {
  const { data, error, loading, reload } = useAsync((signal) => listRepositories(signal), []);
  const [filter, setFilter] = useState('');
  const processing = data?.some((r) => r.status === 'INGESTING') ?? false;

  useEffect(() => {
    if (!processing) return;
    const timer = window.setInterval(reload, 4000);
    return () => window.clearInterval(timer);
  }, [processing, reload]);

  const shown = (data ?? []).filter((r) => `${r.owner}/${r.repoName}`.toLowerCase().includes(filter.trim().toLowerCase()));

  return (
    <>
      <div className="page-head">
        <div><h1>Repositories</h1><p>Connect a public GitHub repository, then fetch, index and explore it.</p></div>
        <Button icon="refresh" onClick={reload} busy={loading && !!data}>Refresh</Button>
      </div>
      <div className="grid-2">
        <ConnectForm />
        <Panel title={`Your repositories${data ? ` (${data.length})` : ''}`} flush
          actions={data && data.length > 5 ? <input className="input" style={{ width: 160, padding: '2px 6px' }} placeholder="Filter" aria-label="Filter repositories" value={filter} onChange={(e) => setFilter(e.target.value)} /> : undefined}>
          {loading && !data ? <Loading text="Loading repositories" />
            : error && !data ? <div style={{ padding: 16 }}><ErrorNotice message={error.message} onRetry={reload} title="Could not load repositories" /></div>
            : data && data.length === 0 ? (
              <EmptyState title="No repositories yet">Connect a GitHub repository using the form to start understanding its code.</EmptyState>
            ) : (
              <>
                {error && <div style={{ padding: 12 }}><ErrorNotice message={error.message} onRetry={reload} title="Refresh failed" /></div>}
                <table className="table">
                  <thead><tr><th>Repository</th><th>Status</th><th className="hide-sm">Branch</th><th className="num">Files</th><th className="hide-sm">Added</th><th /></tr></thead>
                  <tbody>{shown.map((repo) => <RepoRow key={repo.id} repo={repo} onDeleted={reload} />)}</tbody>
                </table>
                {shown.length === 0 && <EmptyState title="No match">No repository matches “{filter}”. ({plural(data?.length ?? 0, 'repository')} total)</EmptyState>}
                <p className="muted" style={{ padding: '8px 12px', margin: 0, fontSize: 12 }}>
                  Deleting removes the repository and its stored files. The AI index is not removed (the AI Service has no delete endpoint yet).
                </p>
              </>
            )}
        </Panel>
      </div>
    </>
  );
}
