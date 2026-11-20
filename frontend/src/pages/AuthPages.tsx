import { useState, type FormEvent } from 'react';
import { useAuth } from '../auth/AuthContext';
import { Button, ErrorNotice, Field, InfoNotice, Panel } from '../components/ui';
import { Link, Navigate, useNavigate, useSearchParams } from '../router/Router';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function useAuthForm() {
  const { status, notice, clearNotice } = useAuth();
  const navigate = useNavigate();
  const next = useSearchParams().get('next');
  const target = next && next.startsWith('/') && !next.startsWith('//') ? next : '/repositories';
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (action: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    clearNotice();
    try {
      await action();
      navigate(target, { replace: true });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setBusy(false);
    }
  };
  return { status, notice, busy, error, submit, target };
}

export function LoginPage() {
  const { login } = useAuth();
  const form = useAuthForm();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});

  if (form.status === 'authenticated') return <Navigate to={form.target} />;

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    const next = {
      email: EMAIL.test(email.trim()) ? undefined : 'Enter a valid email address',
      password: password ? undefined : 'Enter your password',
    };
    setErrors(next);
    if (next.email || next.password) return;
    void form.submit(() => login(email.trim(), password));
  };

  return (
    <Panel title="Sign in">
      <form onSubmit={onSubmit} noValidate>
        {form.notice && <div style={{ marginBottom: 16 }}><InfoNotice>{form.notice}</InfoNotice></div>}
        {form.error && <div style={{ marginBottom: 16 }}><ErrorNotice title="Sign-in failed" message={form.error} /></div>}
        <Field label="Email" type="email" autoComplete="email" value={email} error={errors.email} onChange={(e) => setEmail(e.target.value)} />
        <Field label="Password" type="password" autoComplete="current-password" value={password} error={errors.password} onChange={(e) => setPassword(e.target.value)} />
        <Button type="submit" variant="primary" busy={form.busy} icon="send">{form.busy ? 'Signing in' : 'Sign in'}</Button>
        <div className="auth__switch">No account yet? <Link to="/register">Create one</Link></div>
      </form>
    </Panel>
  );
}

export function RegisterPage() {
  const { register } = useAuth();
  const form = useAuthForm();
  const [values, setValues] = useState({ username: '', email: '', password: '' });
  const [errors, setErrors] = useState<Record<string, string | undefined>>({});

  if (form.status === 'authenticated') return <Navigate to={form.target} />;

  const set = (key: keyof typeof values) => (e: { target: { value: string } }) => setValues({ ...values, [key]: e.target.value });

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    const name = values.username.trim();
    const next = {
      username: name.length >= 3 && name.length <= 50 ? undefined : 'Username must be 3 to 50 characters',
      email: EMAIL.test(values.email.trim()) ? undefined : 'Enter a valid email address',
      password: values.password.length >= 8 && values.password.length <= 100 ? undefined : 'Password must be 8 to 100 characters',
    };
    setErrors(next);
    if (next.username || next.email || next.password) return;
    void form.submit(() => register(name, values.email.trim(), values.password));
  };

  return (
    <Panel title="Create account">
      <form onSubmit={onSubmit} noValidate>
        {form.error && <div style={{ marginBottom: 16 }}><ErrorNotice title="Registration failed" message={form.error} /></div>}
        <Field label="Username" autoComplete="username" value={values.username} error={errors.username} onChange={set('username')} />
        <Field label="Email" type="email" autoComplete="email" value={values.email} error={errors.email} onChange={set('email')} />
        <Field label="Password" type="password" autoComplete="new-password" value={values.password} error={errors.password}
          hint="At least 8 characters" onChange={set('password')} />
        <Button type="submit" variant="primary" busy={form.busy} icon="send">{form.busy ? 'Creating account' : 'Create account'}</Button>
        <div className="auth__switch">Already registered? <Link to="/login">Sign in</Link></div>
      </form>
    </Panel>
  );
}
