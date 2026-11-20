import { useAuth } from '../auth/AuthContext';
import { Button, Panel } from '../components/ui';

export function AccountPage() {
  const { user, logout } = useAuth();
  return (
    <>
      <div className="page-head"><div><h1>Account</h1><p>Signed-in user profile from the User Service.</p></div></div>
      <div style={{ maxWidth: 560 }}>
        <Panel title="Profile">
          <dl className="kv">
            <dt>Username</dt><dd>{user?.username}</dd>
            <dt>Email</dt><dd>{user?.email}</dd>
            <dt>User ID</dt><dd className="mono">{user?.id}</dd>
          </dl>
          <div style={{ marginTop: 16 }}><Button icon="power" onClick={logout}>Sign out</Button></div>
        </Panel>
      </div>
    </>
  );
}
