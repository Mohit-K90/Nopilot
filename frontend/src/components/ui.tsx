import { useEffect, useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react';
import { Icon, type IconName } from './Icon';
import type { Lamp } from '../utils/status';

export function Panel({ title, actions, children, flush = false, className = '' }: {
  title: string; actions?: ReactNode; children: ReactNode; flush?: boolean; className?: string;
}) {
  return (
    <section className={`panel ${className}`}>
      <header className="panel__head">
        <span className="panel__title">{title}</span>
        {actions}
      </header>
      <div className={flush ? 'panel__body panel__body--flush' : 'panel__body'}>{children}</div>
    </section>
  );
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'default' | 'primary' | 'danger' | 'ghost';
  small?: boolean;
  icon?: IconName;
  busy?: boolean;
}

export function Button({ variant = 'default', small, icon, busy, children, className = '', disabled, type = 'button', ...rest }: ButtonProps) {
  const classes = ['btn', variant !== 'default' && `btn--${variant}`, small && 'btn--small', className].filter(Boolean).join(' ');
  return (
    <button type={type} className={classes} disabled={disabled || busy} aria-busy={busy || undefined} {...rest}>
      {icon && <Icon name={busy ? 'refresh' : icon} />}
      {children}
    </button>
  );
}

export function StatusLamp({ lamp, label }: { lamp: Lamp; label: string }) {
  return (
    <span className="status">
      <span className={`lamp lamp--${lamp}`} aria-hidden="true" />
      {label}
    </span>
  );
}

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: string;
  mono?: boolean;
}

export function Field({ label, error, hint, mono, id, ...rest }: FieldProps) {
  const inputId = id ?? `f-${label.toLowerCase().replace(/\W+/g, '-')}`;
  return (
    <div className="field">
      <label className="label" htmlFor={inputId}>{label}</label>
      <input id={inputId} className={`input${mono ? ' input--mono' : ''}`} aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${inputId}-err` : hint ? `${inputId}-hint` : undefined} {...rest} />
      {error ? <span id={`${inputId}-err`} className="field__error" role="alert">{error}</span>
        : hint ? <span id={`${inputId}-hint`} className="field__hint">{hint}</span> : null}
    </div>
  );
}

export function TextArea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...props} />;
}

export function ErrorNotice({ message, onRetry, title = 'Request failed' }: { message: string; onRetry?: () => void; title?: string }) {
  return (
    <div className="notice notice--error" role="alert">
      <Icon name="warn" />
      <div className="notice__body"><strong>{title}</strong><br />{message}</div>
      {onRetry && <Button small icon="refresh" onClick={onRetry}>Retry</Button>}
    </div>
  );
}

export function InfoNotice({ children }: { children: ReactNode }) {
  return (
    <div className="notice notice--info" role="status">
      <Icon name="info" />
      <div className="notice__body">{children}</div>
    </div>
  );
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      <h3>{title}</h3>
      {children && <p>{children}</p>}
      {action}
    </div>
  );
}

export function Loading({ text = 'Loading' }: { text?: string }) {
  return (
    <div role="status" aria-live="polite" style={{ padding: 16 }}>
      <div className="label" style={{ marginBottom: 6 }}>{text}…</div>
      <div className="bar" />
    </div>
  );
}

/** Seconds elapsed while `active` is true (for long synchronous operations). */
export function useElapsed(active: boolean): number {
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    if (!active) {
      setSeconds(0);
      return;
    }
    const started = Date.now();
    const timer = window.setInterval(() => setSeconds(Math.floor((Date.now() - started) / 1000)), 1000);
    return () => window.clearInterval(timer);
  }, [active]);
  return seconds;
}
