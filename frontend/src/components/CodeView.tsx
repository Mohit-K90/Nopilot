import { useEffect, useMemo, useRef, useState } from 'react';
import { highlightToHtml, pickLanguage, splitHtmlLines } from '../utils/highlight';
import { Button } from './ui';

const LINE_CAP = 1500;       // lines rendered before "show all"
const HIGHLIGHT_CAP = 4000;  // above this many lines, skip syntax highlighting (plain text)

export function CodeView({ code, languageLabel, path, hitFrom, hitTo }: {
  code: string; languageLabel?: string; path: string; hitFrom?: number; hitTo?: number;
}) {
  const [showAll, setShowAll] = useState(false);
  const hitRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => setShowAll(false), [path]);

  const lines = useMemo(() => {
    const text = code.replace(/\r\n/g, '\n');
    const count = text.split('\n').length;
    const language = count > HIGHLIGHT_CAP ? null : pickLanguage(languageLabel, path);
    return splitHtmlLines(highlightToHtml(text, language));
  }, [code, languageLabel, path]);

  const mustReveal = hitTo !== undefined && hitTo > LINE_CAP;
  const visible = showAll || mustReveal ? lines : lines.slice(0, LINE_CAP);

  useEffect(() => {
    hitRef.current?.scrollIntoView({ block: 'center' });
  }, [path, hitFrom]);

  return (
    <div>
      <div className="code" tabIndex={0} aria-label={`Source of ${path}`}>
        {visible.map((html, index) => {
          const number = index + 1;
          const hit = hitFrom !== undefined && number >= hitFrom && number <= (hitTo ?? hitFrom);
          return (
            <div key={number} className={hit ? 'code__line code__line--hit' : 'code__line'} ref={hit && number === hitFrom ? hitRef : undefined}>
              <span className="code__no">{number}</span>
              <span className="code__text" dangerouslySetInnerHTML={{ __html: html || ' ' }} />
            </div>
          );
        })}
      </div>
      {visible.length < lines.length && (
        <div className="row" style={{ padding: 10, background: 'var(--paper-3)', borderTop: '1px solid var(--ink)' }}>
          <span className="muted">Showing the first {LINE_CAP} of {lines.length} lines.</span>
          <Button small onClick={() => setShowAll(true)}>Show all lines</Button>
        </div>
      )}
    </div>
  );
}
