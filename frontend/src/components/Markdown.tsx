import { Fragment, type ReactNode } from 'react';
import { Lexer } from 'marked';
import { highlightToHtml, pickLanguage, splitHtmlLines } from '../utils/highlight';

/**
 * Renders markdown WITHOUT innerHTML: marked only tokenises, React builds the elements.
 * Raw HTML in the text is shown as plain text and links are limited to http(s)/mailto,
 * so model-generated markdown cannot inject markup or scripts.
 */
/** Loose token shape: only the fields this renderer reads (independent of marked's own typings). */
interface Tok {
  type: string; raw: string; text?: string; tokens?: Tok[]; items?: Tok[]; depth?: number; lang?: string;
  ordered?: boolean; start?: number | ''; href?: string;
  header?: { tokens: Tok[] }[]; rows?: { tokens: Tok[] }[][];
}

const lex = (text: string): Tok[] => Lexer.lex(text) as unknown as Tok[];

export const slug = (text: string): string => text.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

export function headingsOf(markdown: string): { depth: number; text: string; id: string }[] {
  return lex(markdown)
    .filter((t) => t.type === 'heading' && (t.depth ?? 9) <= 3)
    .map((t) => ({ depth: t.depth ?? 2, text: plain(t.text ?? ''), id: slug(plain(t.text ?? '')) }));
}

const plain = (text: string): string => text.replace(/[`*_]/g, '');
const safeHref = (href?: string): string | null => (href && /^(https?:|mailto:)/i.test(href) ? href : null);

function inline(tokens: Tok[] | undefined): ReactNode {
  return tokens?.map((token, i) => {
    switch (token.type) {
      case 'strong': return <strong key={i}>{inline(token.tokens)}</strong>;
      case 'em': return <em key={i}>{inline(token.tokens)}</em>;
      case 'del': return <del key={i}>{inline(token.tokens)}</del>;
      case 'codespan': return <code key={i}>{token.text}</code>;
      case 'br': return <br key={i} />;
      case 'link': {
        const href = safeHref(token.href);
        return href ? <a key={i} href={href} target="_blank" rel="noopener noreferrer">{inline(token.tokens)}</a> : <Fragment key={i}>{inline(token.tokens)}</Fragment>;
      }
      case 'image': return <Fragment key={i}>{token.text}</Fragment>;
      case 'escape': case 'text': case 'html':
        return token.tokens ? <Fragment key={i}>{inline(token.tokens)}</Fragment> : <Fragment key={i}>{token.text ?? token.raw}</Fragment>;
      default: return <Fragment key={i}>{token.raw}</Fragment>;
    }
  });
}

function blocks(tokens: Tok[]): ReactNode {
  return tokens.map((token, i) => {
    switch (token.type) {
      case 'heading': {
        const Tag = `h${Math.min(token.depth ?? 2, 4)}` as 'h1' | 'h2' | 'h3' | 'h4';
        return <Tag key={i} id={slug(plain(token.text ?? ''))}>{inline(token.tokens)}</Tag>;
      }
      case 'paragraph': return <p key={i}>{inline(token.tokens)}</p>;
      case 'text': return <p key={i}>{token.tokens ? inline(token.tokens) : token.text}</p>;
      case 'code': {
        const language = pickLanguage(undefined, `x.${token.lang ?? ''}`) ?? (token.lang ?? null);
        const html = splitHtmlLines(highlightToHtml(token.text ?? '', language)).join('\n');
        return <pre key={i}><code dangerouslySetInnerHTML={{ __html: html }} /></pre>;
      }
      case 'blockquote': return <blockquote key={i}>{blocks(token.tokens ?? [])}</blockquote>;
      case 'list': {
        const Tag = token.ordered ? 'ol' : 'ul';
        return (
          <Tag key={i} start={token.ordered && typeof token.start === 'number' ? token.start : undefined}>
            {(token.items ?? []).map((item, j) => <li key={j}>{blocks(item.tokens ?? [])}</li>)}
          </Tag>
        );
      }
      case 'hr': return <hr key={i} />;
      case 'table':
        return (
          <table key={i}>
            <thead><tr>{(token.header ?? []).map((cell, j) => <th key={j}>{inline(cell.tokens)}</th>)}</tr></thead>
            <tbody>{(token.rows ?? []).map((row, r) => <tr key={r}>{row.map((cell, j) => <td key={j}>{inline(cell.tokens)}</td>)}</tr>)}</tbody>
          </table>
        );
      case 'html': return <p key={i}>{token.text ?? token.raw}</p>;
      case 'space': return null;
      default: return token.raw ? <p key={i}>{token.raw}</p> : null;
    }
  });
}

export function Markdown({ text }: { text: string }) {
  return <div className="md">{blocks(lex(text))}</div>;
}
