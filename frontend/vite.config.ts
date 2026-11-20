import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// In development the browser talks only to the Vite server (same origin), which forwards
// each API prefix to its service. The backend services do not configure CORS, so this is
// what makes the browser calls work without touching them. When an API Gateway exists,
// point every prefix at it (or set VITE_API_URL) - no frontend code changes needed.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const user = env.DEV_USER_SERVICE_URL || 'http://localhost:8081';
  const repo = env.DEV_REPOSITORY_SERVICE_URL || 'http://localhost:8082';
  const ai = env.DEV_AI_SERVICE_URL || 'http://localhost:8083';

  const proxy = {
    '/api/auth': { target: user, changeOrigin: true },
    '/api/users': { target: user, changeOrigin: true },
    '/api/repositories': { target: repo, changeOrigin: true },
    // AI ingestion is synchronous and can take minutes: no proxy timeout.
    '/api/ai': { target: ai, changeOrigin: true, timeout: 0, proxyTimeout: 0 },
  };

  return {
    plugins: [react()],
    server: { port: 5173, proxy },
    preview: { port: 4173, proxy },
  };
});
