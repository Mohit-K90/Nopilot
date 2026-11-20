import type { ChatSource, GuideFacts } from '../api/types';

/** Generated guides cost a local-LLM run, so the last result is kept per repository in the browser. */
export interface StoredGuide {
  guide: string;
  facts: GuideFacts;
  generatedAt: string;
}

export function loadGuide(id: string): StoredGuide | null {
  try {
    const raw = localStorage.getItem(`nopilot.guide.${id}`);
    return raw ? (JSON.parse(raw) as StoredGuide) : null;
  } catch {
    return null;
  }
}

export const saveGuide = (id: string, value: StoredGuide): void => {
  try {
    localStorage.setItem(`nopilot.guide.${id}`, JSON.stringify(value));
  } catch {
    /* storage full or disabled: the guide is simply not persisted */
  }
};

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  sources?: ChatSource[];
}

const MAX_MESSAGES = 60;

export function loadChat(id: string): ChatMessage[] {
  try {
    const raw = sessionStorage.getItem(`nopilot.chat.${id}`);
    return raw ? (JSON.parse(raw) as ChatMessage[]) : [];
  } catch {
    return [];
  }
}

export function saveChat(id: string, messages: ChatMessage[]): void {
  try {
    sessionStorage.setItem(`nopilot.chat.${id}`, JSON.stringify(messages.slice(-MAX_MESSAGES)));
  } catch {
    /* ignore */
  }
}
