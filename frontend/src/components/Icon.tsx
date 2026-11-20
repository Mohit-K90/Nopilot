import type { ReactNode } from 'react';

/** Small 16px line icons drawn on a pixel grid (no icon library, no emoji). */
const PATHS: Record<string, ReactNode> = {
  folder: <path d="M1.5 3.5h4l1.5 1.5h7.5v8h-13z" />,
  file: <path d="M3.5 1.5h6l3 3v10h-9zM9.5 1.5v3h3" />,
  overview: <path d="M2.5 2.5h4.5v4.5h-4.5zM9 2.5h4.5v4.5h-4.5zM2.5 9h4.5v4.5h-4.5zM9 9h4.5v4.5h-4.5z" />,
  terminal: <path d="M1.5 2.5h13v11h-13zM4 6l2.5 2L4 10M8 10.5h4" />,
  chip: <path d="M4.5 4.5h7v7h-7zM6.5 1.5v3M9.5 1.5v3M6.5 11.5v3M9.5 11.5v3M1.5 6.5h3M1.5 9.5h3M11.5 6.5h3M11.5 9.5h3" />,
  book: <path d="M2 2.5h4.5A1.5 1.5 0 0 1 8 4v9.5a1 1 0 0 0-1-1H2zM14 2.5H9.5A1.5 1.5 0 0 0 8 4v9.5a1 1 0 0 1 1-1h5z" />,
  power: <path d="M8 1.5v6M4.5 4a5.5 5.5 0 1 0 7 0" />,
  plus: <path d="M8 2.5v11M2.5 8h11" />,
  x: <path d="M3 3l10 10M13 3L3 13" />,
  refresh: <path d="M13.5 8a5.5 5.5 0 1 1-1.8-4.1M13.5 2v3.5H10" />,
  search: <path d="M6.5 2.5a4 4 0 1 1 0 8 4 4 0 0 1 0-8zM9.5 9.5l4 4" />,
  trash: <path d="M2.5 4.5h11M6 4.5v-2h4v2M4 4.5l.5 9h7l.5-9M6.5 7v4M9.5 7v4" />,
  link: <path d="M9 2.5h4.5V7M13.5 2.5L7 9M11.5 9.5v4h-9v-9h4" />,
  check: <path d="M2.5 8.5l3.5 3.5 7.5-8" />,
  warn: <path d="M8 1.8l6.5 11.7h-13zM8 6v3.5M8 11.2v.6" />,
  user: <path d="M8 2.5a2.7 2.7 0 1 1 0 5.4 2.7 2.7 0 0 1 0-5.4zM2.5 14c.4-3 2.6-4.5 5.5-4.5s5.1 1.5 5.5 4.5z" />,
  database: <path d="M3 4c0-1.1 2.2-2 5-2s5 .9 5 2-2.2 2-5 2-5-.9-5-2zM3 4v8c0 1.1 2.2 2 5 2s5-.9 5-2V4M3 8c0 1.1 2.2 2 5 2s5-.9 5-2" />,
  chevron: <path d="M6 3l5 5-5 5" />,
  chevdown: <path d="M3 6l5 5 5-5" />,
  send: <path d="M1.5 8h11M8.5 3.5L13 8l-4.5 4.5" />,
  download: <path d="M8 1.5v8M4.5 6.5L8 10l3.5-3.5M2.5 13.5h11" />,
  menu: <path d="M2 4h12M2 8h12M2 12h12" />,
  info: <path d="M8 1.5a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13zM8 7v4.5M8 4.6v.6" />,
};

export type IconName = keyof typeof PATHS;

export function Icon({ name, title }: { name: IconName; title?: string }) {
  return (
    <svg className="icon" viewBox="0 0 16 16" role={title ? 'img' : undefined} aria-hidden={title ? undefined : true} aria-label={title}>
      {PATHS[name]}
    </svg>
  );
}
