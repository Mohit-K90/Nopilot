import type { ReactNode } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { getRepositoryContents, getRepositoryFiles } from '../api/repositories';
import type { RepoFileContent, RepoFileMeta } from '../api/types';
import { CodeView } from '../components/CodeView';
import { Icon } from '../components/Icon';
import { Button, EmptyState, ErrorNotice, InfoNotice, Loading, Panel } from '../components/ui';
import { useAsync } from '../hooks/useAsync';
import { Link, useNavigate, useSearchParams } from '../router/Router';
import { getCachedContents, setCachedContents } from '../utils/cache';
import { formatBytes } from '../utils/format';
import { useRepo } from './RepoContext';

interface Node { name: string; path: string; dirs: Map<string, Node>; files: RepoFileMeta[]; count: number; }

function buildTree(files: RepoFileMeta[]): Node {
  const root: Node = { name: '', path: '', dirs: new Map(), files: [], count: 0 };
  for (const file of files) {
    const parts = file.path.split('/');
    let node = root;
    node.count++;
    for (let i = 0; i < parts.length - 1; i++) {
      const path = parts.slice(0, i + 1).join('/');
      if (!node.dirs.has(parts[i])) node.dirs.set(parts[i], { name: parts[i], path, dirs: new Map(), files: [], count: 0 });
      node = node.dirs.get(parts[i])!;
      node.count++;
    }
    node.files.push(file);
  }
  return root;
}

function Row({ depth, children }: { depth: number; children: ReactNode }) {
  return <div style={{ paddingLeft: depth * 14 }}>{children}</div>;
}

function TreeView({ node, depth, open, toggle, selected, select }: {
  node: Node; depth: number; open: Set<string>; toggle: (p: string) => void; selected: string | null; select: (p: string) => void;
}) {
  const dirs = [...node.dirs.values()].sort((a, b) => a.name.localeCompare(b.name));
  const files = [...node.files].sort((a, b) => a.path.localeCompare(b.path));
  return (
    <>
      {dirs.map((dir) => (
        <div key={dir.path}>
          <Row depth={depth}>
            <button className="dir" aria-expanded={open.has(dir.path)} onClick={() => toggle(dir.path)}>
              <Icon name={open.has(dir.path) ? 'chevdown' : 'chevron'} /><Icon name="folder" />{dir.name}<span className="meta">{dir.count}</span>
            </button>
          </Row>
          {open.has(dir.path) && <TreeView node={dir} depth={depth + 1} open={open} toggle={toggle} selected={selected} select={select} />}
        </div>
      ))}
      {files.map((file) => (
        <Row key={file.path} depth={depth}>
          <button aria-current={selected === file.path} onClick={() => select(file.path)} title={file.path}>
            <span style={{ width: 16, flex: 'none' }} /><Icon name="file" />{file.path.split('/').pop()}<span className="meta">{formatBytes(file.size)}</span>
          </button>
        </Row>
      ))}
    </>
  );
}

function Highlighted({ text, query }: { text: string; query: string }) {
  const at = text.toLowerCase().indexOf(query.toLowerCase());
  if (at < 0 || !query) return <>{text}</>;
  return <>{text.slice(0, at)}<mark>{text.slice(at, at + query.length)}</mark>{text.slice(at + query.length)}</>;
}

export function FilesPage() {
  const { repo } = useRepo();
  const navigate = useNavigate();
  const params = useSearchParams();
  const selected = params.get('path');
  const hitFrom = params.get('from') ? Number(params.get('from')) : undefined;
  const hitTo = params.get('to') ? Number(params.get('to')) : hitFrom;
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState<Set<string>>(new Set());
  const viewerRef = useRef<HTMLDivElement>(null);

  const meta = useAsync((signal) => getRepositoryFiles(repo.id, signal), [repo.id, repo.ingestedAt]);
  const [contents, setContents] = useState<Map<string, RepoFileContent> | null>(null);
  const [contentsError, setContentsError] = useState<string | null>(null);

  const loadContents = useCallback(() => {
    setContentsError(null);
    const cached = getCachedContents(repo.id);
    if (cached) { setContents(new Map(cached.map((f) => [f.path, f]))); return () => undefined; }
    const controller = new AbortController();
    getRepositoryContents(repo.id, controller.signal)
      .then((list) => { setCachedContents(repo.id, list); setContents(new Map(list.map((f) => [f.path, f]))); })
      .catch((e: unknown) => { if (!(e instanceof DOMException)) setContentsError(e instanceof Error ? e.message : String(e)); });
    return () => controller.abort();
  }, [repo.id]);

  useEffect(() => { setContents(null); return loadContents(); }, [loadContents, repo.ingestedAt]);

  const tree = useMemo(() => (meta.data ? buildTree(meta.data.files) : null), [meta.data]);
  const byPath = useMemo(() => new Map((meta.data?.files ?? []).map((f) => [f.path, f])), [meta.data]);

  const select = useCallback((path: string, from?: number, to?: number) => {
    const q = new URLSearchParams({ path });
    if (from) { q.set('from', String(from)); q.set('to', String(to ?? from)); }
    navigate(`/repositories/${repo.id}/files?${q.toString()}`);
    if (window.innerWidth < 960) setTimeout(() => viewerRef.current?.scrollIntoView({ behavior: 'smooth' }), 50);
  }, [navigate, repo.id]);

  // Open the folders leading to the selected file; default to a README when nothing is selected.
  useEffect(() => {
    if (!meta.data) return;
    if (selected) {
      const parts = selected.split('/');
      setOpen((prev) => new Set([...prev, ...parts.slice(0, -1).map((_, i) => parts.slice(0, i + 1).join('/'))]));
    } else {
      const readme = meta.data.files.map((f) => f.path).filter((p) => /(^|\/)readme/i.test(p)).sort((a, b) => a.length - b.length)[0];
      if (readme) navigate(`/repositories/${repo.id}/files?path=${encodeURIComponent(readme)}`, { replace: true });
    }
  }, [meta.data, selected, navigate, repo.id]);

  if (repo.status !== 'READY') {
    return <EmptyState title="No files to browse yet" action={<Link className="btn btn--primary" to={`/repositories/${repo.id}`}>Go to overview</Link>}>
      Fetch the repository from GitHub first (current status: {repo.status}).</EmptyState>;
  }
  if (meta.loading && !meta.data) return <Loading text="Loading file list" />;
  if (meta.error || !tree) return <ErrorNotice title="Could not load files" message={meta.error?.message ?? 'No data'} onRetry={meta.reload} />;

  const q = query.trim();
  const matches = q ? meta.data!.files.filter((f) => f.path.toLowerCase().includes(q.toLowerCase())) : [];
  const toggle = (p: string) => setOpen((prev) => { const next = new Set(prev); if (next.has(p)) next.delete(p); else next.add(p); return next; });
  const file = selected ? contents?.get(selected) : undefined;
  const info = selected ? byPath.get(selected) : undefined;
  const lines = file ? file.content.split('\n').length : 0;

  return (
    <div className="files">
      <Panel title={`Files (${meta.data!.fileCount})`} flush>
        <div style={{ padding: 8, borderBottom: '1px solid var(--line)' }}>
          <input className="input input--mono" placeholder="Filter by path…" aria-label="Filter files" value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        <div className="tree" role="tree">
          {q ? (
            matches.length === 0 ? <p className="muted" style={{ padding: 12 }}>No file path contains “{q}”.</p>
              : matches.slice(0, 300).map((f) => (
                <button key={f.path} aria-current={selected === f.path} onClick={() => select(f.path)} title={f.path}>
                  <Icon name="file" /><span><Highlighted text={f.path} query={q} /></span>
                </button>
              ))
          ) : <TreeView node={tree} depth={0} open={open} toggle={toggle} selected={selected} select={select} />}
          {q && matches.length > 300 && <p className="muted" style={{ padding: 8 }}>Showing 300 of {matches.length} matches. Refine the filter.</p>}
        </div>
      </Panel>

      <div ref={viewerRef}>
        <Panel title={selected ?? 'Source viewer'} flush>
          {!selected ? (
            <EmptyState title="Select a file">Choose a file from the list to read its stored source.</EmptyState>
          ) : (
            <>
              <div className="viewer__meta" aria-label="File information">
                <span><b>Metadata</b></span>
                <span><b>Language</b>{info?.language ?? file?.language ?? '—'}</span>
                <span><b>Size</b>{info ? formatBytes(info.size) : '—'}</span>
                <span><b>Lines</b>{file ? lines : '—'}</span>
                <span><b>Branch</b>{meta.data!.defaultBranch ?? '—'}</span>
              </div>
              {contentsError ? (
                <div style={{ padding: 12 }}><ErrorNotice title="Could not load source" message={contentsError} onRetry={loadContents} /></div>
              ) : !contents ? (
                <Loading text="Loading source for all files (downloaded once)" />
              ) : !file ? (
                <div style={{ padding: 12 }}><InfoNotice>No stored source for <code>{selected}</code>. It may have been skipped or removed by a later fetch.</InfoNotice></div>
              ) : (
                <CodeView code={file.content} languageLabel={file.language} path={file.path} hitFrom={hitFrom} hitTo={hitTo} />
              )}
              {selected && <div style={{ padding: 8, borderTop: '1px solid var(--ink)' }} className="row">
                <Button small onClick={() => navigate(`/repositories/${repo.id}/assistant?ask=${encodeURIComponent(`Explain ${selected}`)}`)}>Ask the assistant about this file</Button>
              </div>}
            </>
          )}
        </Panel>
      </div>
    </div>
  );
}
