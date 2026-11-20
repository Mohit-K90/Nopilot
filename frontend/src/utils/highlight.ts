import hljs from 'highlight.js/lib/core';
import java from 'highlight.js/lib/languages/java';
import python from 'highlight.js/lib/languages/python';
import javascript from 'highlight.js/lib/languages/javascript';
import typescript from 'highlight.js/lib/languages/typescript';
import xml from 'highlight.js/lib/languages/xml';
import json from 'highlight.js/lib/languages/json';
import yaml from 'highlight.js/lib/languages/yaml';
import properties from 'highlight.js/lib/languages/properties';
import markdown from 'highlight.js/lib/languages/markdown';
import dockerfile from 'highlight.js/lib/languages/dockerfile';
import bash from 'highlight.js/lib/languages/bash';
import sql from 'highlight.js/lib/languages/sql';

const REGISTERED: Record<string, Parameters<typeof hljs.registerLanguage>[1]> = {
  java, python, javascript, typescript, xml, json, yaml, properties, markdown, dockerfile, bash, sql,
};
Object.entries(REGISTERED).forEach(([name, definition]) => hljs.registerLanguage(name, definition));

const BY_LABEL: Record<string, string> = {
  JAVA: 'java', PYTHON: 'python', JAVASCRIPT: 'javascript', TYPESCRIPT: 'typescript', DOCUMENTATION: 'markdown',
};
const BY_EXTENSION: Record<string, string> = {
  xml: 'xml', json: 'json', yml: 'yaml', yaml: 'yaml', properties: 'properties', md: 'markdown', sh: 'bash', sql: 'sql',
};

/** Picks a highlight.js language from the backend language label, then from the file name. */
export function pickLanguage(label: string | undefined, path: string): string | null {
  if (label && BY_LABEL[label]) {
    return BY_LABEL[label];
  }
  const name = path.split('/').pop() ?? '';
  if (/^dockerfile/i.test(name)) {
    return 'dockerfile';
  }
  return BY_EXTENSION[name.split('.').pop()?.toLowerCase() ?? ''] ?? null;
}

export function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/** highlight.js output is escaped HTML, so it is safe to inject. Unknown languages fall back to escaped text. */
export function highlightToHtml(code: string, language: string | null): string {
  if (!language || !hljs.getLanguage(language)) {
    return escapeHtml(code);
  }
  try {
    return hljs.highlight(code, { language, ignoreIllegals: true }).value;
  } catch {
    return escapeHtml(code);
  }
}

/** Splits highlighted HTML into per-line HTML, re-opening spans that cross line breaks. */
export function splitHtmlLines(html: string): string[] {
  const lines: string[] = [];
  const open: string[] = [];
  for (const raw of html.split('\n')) {
    const prefix = open.join('');
    const tags = /<(\/?)span([^>]*)>/g;
    let match: RegExpExecArray | null;
    while ((match = tags.exec(raw)) !== null) {
      if (match[1]) {
        open.pop();
      } else {
        open.push(`<span${match[2]}>`);
      }
    }
    lines.push(prefix + raw + '</span>'.repeat(open.length));
  }
  return lines;
}
