import type { ReactNode } from 'react';

export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <main className="auth">
      <div className="auth__frame">
        <div className="auth__mast">
          <h1>NoPilot</h1>
          <span className="label">Codebase onboarding system · Understand with AI. Engineer without it.</span>
        </div>
        {children}
        <p className="manual">NoPilot explains code. It never writes features or modifies your repositories.</p>
      </div>
    </main>
  );
}
