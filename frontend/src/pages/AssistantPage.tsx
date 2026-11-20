import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { askQuestion } from '../api/ai';
import { isAbort } from '../api/http';
import { Markdown } from '../components/Markdown';
import { Button, ErrorNotice, InfoNotice, Panel, TextArea, useElapsed } from '../components/ui';
import { Link, useSearchParams } from '../router/Router';
import { formatDuration } from '../utils/format';
import { loadChat, loadGuide, saveChat, type ChatMessage } from '../utils/stores';
import { useRepo } from './RepoContext';

const MAX = 1000;
const base = (path: string) => path.split('/').pop() ?? path;

function suggestions(repoName: string, entryPoints: string[], important: string[]): string[] {
  const contextual = [
    ...entryPoints.slice(0, 1).map((p) => `What does ${base(p)} do and how does the application start?`),
    ...important.filter((p) => !entryPoints.includes(p)).slice(0, 2).map((p) => `Explain the role of ${base(p)}.`),
  ];
  return [
    `Explain the architecture of ${repoName}.`,
    'Where does the application start?',
    'Explain the authentication flow.',
    'What are the most important components?',
    'How does data flow through this application?',
    'Which dependencies does this project rely on, and what for?',
    ...contextual,
  ].slice(0, 6 + contextual.length);
}

export function AssistantPage() {
  const { repo, ai, busy, runAi } = useRepo();
  const prefill = useSearchParams().get('ask');
  const [messages, setMessages] = useState<ChatMessage[]>(() => loadChat(repo.id));
  const [input, setInput] = useState(prefill ?? '');
  const [pending, setPending] = useState<string | null>(null);
  const [error, setError] = useState<{ question: string; message: string } | null>(null);
  const controller = useRef<AbortController | null>(null);
  const logRef = useRef<HTMLDivElement>(null);
  const elapsed = useElapsed(pending !== null);
  const ready = ai?.status === 'READY';
  const guide = loadGuide(repo.id);

  useEffect(() => { saveChat(repo.id, messages); }, [repo.id, messages]);
  useEffect(() => { logRef.current?.scrollTo({ top: logRef.current.scrollHeight }); }, [messages, pending, error]);
  useEffect(() => () => controller.current?.abort(), []);

  const ask = useCallback(async (raw: string) => {
    const question = raw.trim();
    if (!question || pending) return;
    setError(null);
    setInput('');
    setMessages((m) => [...m, { id: crypto.randomUUID(), role: 'user', text: question }]);
    setPending(question);
    const abort = new AbortController();
    controller.current = abort;
    try {
      const answer = await askQuestion(repo.id, question, abort.signal);
      setMessages((m) => [...m, { id: crypto.randomUUID(), role: 'assistant', text: answer.answer, sources: answer.sources }]);
    } catch (e) {
      if (!isAbort(e)) setError({ question, message: e instanceof Error ? e.message : String(e) });
    } finally {
      setPending(null);
    }
  }, [pending, repo.id]);

  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); void ask(input); }
  };

  const list = suggestions(repo.repoName, guide?.facts.entryPoints ?? [], guide?.facts.importantFiles ?? []);

  return (
    <div className="assistant">
      <div className="term" aria-label="Repository assistant">
        <div className="term__log" ref={logRef} aria-live="polite">
          {messages.length === 0 && !pending && (
            <div>
              <div className="label">Codebase investigation · {repo.owner}/{repo.repoName}</div>
              <p style={{ margin: '8px 0 12px' }}>Ask about architecture, specific files, dependencies or execution flow. Answers come only from the indexed code.</p>
              <div className="suggest">
                {list.map((s) => <button key={s} disabled={!ready} onClick={() => void ask(s)}>{s}</button>)}
              </div>
            </div>
          )}
          {messages.map((m) => m.role === 'user' ? (
            <div key={m.id} className="term__q">{m.text}</div>
          ) : (
            <div key={m.id} className="term__a">
              <Markdown text={m.text} />
              {m.sources && m.sources.length > 0 ? (
                <div className="sources">
                  <span className="label">Sources</span>
                  {m.sources.map((s) => (
                    <Link key={`${s.citation}${s.filePath}${s.startLine}`} className="source"
                      to={`/repositories/${repo.id}/files?path=${encodeURIComponent(s.filePath)}&from=${s.startLine}&to=${s.endLine}`}>
                      <b>{s.citation}</b><span>{s.filePath}:{s.startLine}-{s.endLine}</span><span>{s.score.toFixed(2)}</span>
                    </Link>
                  ))}
                </div>
              ) : <div className="label" style={{ marginTop: 8 }}>No source references were returned for this answer.</div>}
            </div>
          ))}
          {pending && (
            <div className="term__a" role="status">
              <span className="label">Retrieving context and waiting for the local model · {formatDuration(elapsed)}</span> <span className="cursor" />
              <div style={{ marginTop: 8 }}><Button small onClick={() => controller.current?.abort()}>Cancel</Button></div>
            </div>
          )}
          {error && (
            <ErrorNotice title="Question failed" message={error.message} onRetry={() => void ask(error.question)} />
          )}
        </div>
        <div className="term__input">
          <TextArea rows={2} value={input} maxLength={MAX} disabled={!ready || pending !== null} onKeyDown={onKey}
            onChange={(e) => setInput(e.target.value)} aria-label="Question"
            placeholder={ready ? 'Ask about this repository…  (Enter to send, Shift+Enter for a new line)' : 'Build the AI index to enable questions'} />
          <Button variant="primary" icon="send" disabled={!ready || !input.trim()} busy={pending !== null} onClick={() => void ask(input)}>Ask</Button>
        </div>
      </div>

      <div className="stack">
        {!ready && (
          <Panel title="AI index required">
            <p>{ai?.status === 'INGESTING' ? 'The AI index is being built…' : ai?.status === 'FAILED' ? `The last indexing attempt failed: ${ai.failure_reason ?? 'unknown reason'}` : 'This repository has not been indexed for the assistant yet.'}</p>
            {repo.status === 'READY'
              ? <Button variant="primary" icon="chip" busy={busy === 'ai' || ai?.status === 'INGESTING'} onClick={runAi}>{ai?.status === 'FAILED' ? 'Retry indexing' : 'Build AI index'}</Button>
              : <Link className="btn" to={`/repositories/${repo.id}`}>Fetch the repository first</Link>}
          </Panel>
        )}
        <Panel title="Session">
          <div className="stack" style={{ gap: 10 }}>
            {ready && <div className="label">{ai!.file_count} files · {ai!.chunk_count} chunks indexed</div>}
            <InfoNotice>Each question is answered on its own: no earlier messages are sent to the model. The transcript is kept only in this browser tab.</InfoNotice>
            <p className="muted" style={{ fontSize: 12, margin: 0 }}>NoPilot explains existing code. Requests to write or change code are declined.</p>
            <span className="label">{input.length}/{MAX} characters</span>
            <Button small icon="trash" disabled={messages.length === 0} onClick={() => { setMessages([]); setError(null); }}>Clear transcript</Button>
          </div>
        </Panel>
      </div>
    </div>
  );
}
