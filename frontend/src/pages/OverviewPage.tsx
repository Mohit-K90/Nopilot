import type { ReactNode } from 'react';
import { getRepositoryFiles } from '../api/repositories';
import { Button, ErrorNotice, InfoNotice, Loading, Panel, StatusLamp, EmptyState } from '../components/ui';
import { useAsync } from '../hooks/useAsync';
import { Link } from '../router/Router';
import { formatBytes, formatDate, formatDuration } from '../utils/format';
import { aiStage, githubStage, type StageView } from '../utils/status';
import { loadGuide } from '../utils/stores';
import { useRepo } from './RepoContext';

const COLORS = ['#5c6a3c', '#4d6d83', '#b9791f', '#a4492a', '#6b6c63', '#8a8777', '#262826'];

function Stage({ index, stage, children }: { index: number; stage: StageView; children?: ReactNode }) {
  return (
    <div className="stage">
      <span className="stage__idx">0{index}</span>
      <div className="row" style={{ gap: 10 }}>
        <span className="stage__title">{stage.label}</span>
        <StatusLamp lamp={stage.lamp} label={stage.lamp === 'idle' ? 'Idle' : stage.lamp === 'busy' ? 'Running' : stage.lamp === 'ok' ? 'Done' : 'Failed'} />
      </div>
      <div className="stage__detail">{stage.text}{stage.detail ? ` — ${stage.detail}` : ''}</div>
      {children && <div className="stage__actions">{children}</div>}
    </div>
  );
}

function Languages({ id }: { id: string }) {
  const { data, error, loading, reload } = useAsync((signal) => getRepositoryFiles(id, signal), [id]);
  if (loading) return <Loading text="Reading file metadata" />;
  if (error || !data) return <ErrorNotice message={error?.message ?? 'No data'} onRetry={reload} title="Could not load file metadata" />;
  const counts = new Map<string, number>();
  data.files.forEach((f) => counts.set(f.language, (counts.get(f.language) ?? 0) + 1));
  const rows = [...counts.entries()].sort((a, b) => b[1] - a[1]);
  const total = data.files.length || 1;
  return (
    <>
      <div className="langbar" role="img" aria-label="Files by language">
        {rows.map(([lang, n], i) => <span key={lang} style={{ width: `${(n / total) * 100}%`, background: COLORS[i % COLORS.length] }} title={`${lang}: ${n}`} />)}
      </div>
      <div className="legend">
        {rows.map(([lang, n], i) => <span key={lang}><i style={{ background: COLORS[i % COLORS.length] }} />{lang} · {n}</span>)}
      </div>
    </>
  );
}

export function OverviewPage() {
  const { repo, ai, aiError, busy, elapsed, actionError, runGithub, runAi } = useRepo();
  const github = githubStage(repo, busy === 'github');
  const index = aiStage(repo, ai, busy === 'ai');
  const guide = loadGuide(repo.id);
  const base = `/repositories/${repo.id}`;
  const ready = repo.status === 'READY';
  const aiReady = ai?.status === 'READY';

  return (
    <div className="overview">
      <div className="stack">
        <Panel title="Processing pipeline">
          {actionError && <div style={{ marginBottom: 12 }}><ErrorNotice title="Operation failed" message={actionError} /></div>}
          {aiError && <div style={{ marginBottom: 12 }}><InfoNotice>AI Service status unavailable: {aiError}</InfoNotice></div>}
          {busy && <div style={{ marginBottom: 12 }}><div className="label" style={{ marginBottom: 6 }}>
            {busy === 'github' ? 'Fetching from GitHub' : 'Indexing for AI'} · {formatDuration(elapsed)} — this can take a while, keep this page open</div><div className="bar" /></div>}
          <Stage index={1} stage={github}>
            <Button small variant={repo.status === 'READY' ? 'default' : 'primary'} icon="refresh" busy={busy === 'github'}
              disabled={busy === 'ai' || repo.status === 'INGESTING'} onClick={runGithub}>
              {repo.status === 'READY' ? 'Fetch again' : repo.status === 'FAILED' ? 'Retry fetch' : 'Fetch from GitHub'}
            </Button>
          </Stage>
          <Stage index={2} stage={index}>
            <Button small variant={ready && !aiReady ? 'primary' : 'default'} icon="chip" busy={busy === 'ai'}
              disabled={!ready || busy === 'github' || ai?.status === 'INGESTING'} onClick={runAi}>
              {aiReady ? 'Re-index' : ai?.status === 'FAILED' ? 'Retry indexing' : 'Build AI index'}
            </Button>
          </Stage>
        </Panel>

        <Panel title="Repository information">
          <dl className="kv">
            <dt>Owner</dt><dd>{repo.owner}</dd>
            <dt>Repository</dt><dd>{repo.repoName}</dd>
            <dt>GitHub URL</dt><dd><a href={repo.githubUrl} target="_blank" rel="noopener noreferrer">{repo.githubUrl}</a></dd>
            <dt>Default branch</dt><dd className="mono">{repo.defaultBranch ?? 'unknown until fetched'}</dd>
            <dt>Status</dt><dd className="mono">{repo.status}</dd>
            <dt>Stored files</dt><dd>{repo.fileCount ?? '—'}</dd>
            <dt>Stored size</dt><dd>{formatBytes(repo.totalSizeBytes)}</dd>
            <dt>Skipped files</dt><dd>{repo.skippedFileCount ?? '—'}</dd>
            <dt>Registered</dt><dd>{formatDate(repo.createdAt)}</dd>
            <dt>Last fetched</dt><dd>{formatDate(repo.ingestedAt)}</dd>
            {ai && <><dt>AI indexed</dt><dd>{formatDate(ai.indexed_at)}</dd></>}
          </dl>
        </Panel>

        <Panel title="Languages">
          {ready ? <Languages id={repo.id} /> : <p className="muted" style={{ margin: 0 }}>Available after the repository has been fetched.</p>}
        </Panel>
      </div>

      <div className="stack">
        <Panel title="Quick actions">
          <div className="stack" style={{ gap: 8 }}>
            <Link className={`btn${ready ? '' : ' btn--ghost'}`} to={`${base}/files`} aria-disabled={!ready}>Browse files</Link>
            <Link className="btn" to={`${base}/assistant`}>Ask the AI assistant</Link>
            <Link className="btn" to={`${base}/guide`}>Open onboarding guide</Link>
          </div>
          {!aiReady && ready && <p className="muted" style={{ margin: '12px 0 0', fontSize: 12 }}>The assistant and guide need the AI index (step 02).</p>}
        </Panel>

        <Panel title="Architecture summary">
          {guide ? (
            <div className="facts">
              <h3>Technologies</h3>
              <ul>{guide.facts.technologies.length ? guide.facts.technologies.map((t) => <li key={t}>{t}</li>) : <li>none detected</li>}</ul>
              <h3>Entry points</h3>
              <ul>{guide.facts.entryPoints.length ? guide.facts.entryPoints.map((t) => <li key={t}>{t}</li>) : <li>none detected</li>}</ul>
              <p className="muted" style={{ fontSize: 12, marginTop: 12 }}>From the guide generated {formatDate(guide.generatedAt)}. <Link to={`${base}/guide`}>Open guide</Link></p>
            </div>
          ) : (
            <EmptyState title="No summary yet" action={<Link className="btn btn--small" to={`${base}/guide`}>Go to guide</Link>}>
              Generate the onboarding guide to see technologies and entry points here.
            </EmptyState>
          )}
        </Panel>
      </div>
    </div>
  );
}
