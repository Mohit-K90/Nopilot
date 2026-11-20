# NoPilot frontend

React 19 + TypeScript + Vite. No UI framework: the look (cream paper, charcoal title bars, olive accents,
terminal-style code/assistant panes) is plain CSS in `src/styles/app.css`. Runtime dependencies: react, react-dom,
marked (markdown tokeniser), highlight.js (syntax colours), three @fontsource font packages.

## Run

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
npm run build      # typecheck + production build into dist/
```

Start the backends first (user-service :8081, repository-service :8082, ai-service :8083, plus PostgreSQL and Ollama
for AI features). In development the Vite server **proxies** `/api/auth` and `/api/users` -> 8081, `/api/repositories`
-> 8082, `/api/ai` -> 8083, because none of the services configure CORS. Targets are overridable in `.env`
(`DEV_*_SERVICE_URL`).

## API configuration / API Gateway

All network access is in `src/api/http.ts`. Base URLs come from `VITE_USER_API_URL`, `VITE_REPO_API_URL`,
`VITE_AI_API_URL`, falling back to `VITE_API_URL`, falling back to same-origin (proxy). Pointing everything at a future
gateway = set `VITE_API_URL` (or change the proxy targets). If the frontend is served from a different origin than the
APIs, the APIs must allow CORS.

## Endpoints used

| UI feature | Endpoint |
|---|---|
| Register / login | `POST /api/auth/register`, `POST /api/auth/login` |
| Session / account | `GET /api/users/me` |
| Repository list / detail | `GET /api/repositories`, `GET /api/repositories/{id}` |
| Connect / delete | `POST /api/repositories`, `DELETE /api/repositories/{id}` |
| GitHub fetch | `POST /api/repositories/{id}/ingest` |
| File tree / contents | `GET /api/repositories/{id}/files`, `GET /api/repositories/{id}/contents` |
| AI index | `POST /api/ai/repositories/{id}/ingest`, `GET .../status` |
| Assistant | `POST /api/ai/repositories/{id}/chat` |
| Guide | `GET /api/ai/repositories/{id}/guide` |

## Status mapping (nothing invented)

Repository Service `PENDING / INGESTING / READY / FAILED` = pipeline step 01 "GitHub fetch".
AI Service status row (`404` = not indexed, `INGESTING / READY / FAILED`) = step 02 "AI index".
While either is `INGESTING`, or this tab is waiting on an ingest call, the page polls every 3 s.

## Behaviour worth knowing

* JWT is stored in `localStorage`; any 401 from an authenticated call signs the user out with a notice.
* Both ingest calls are synchronous on the server; the UI keeps the request open, shows elapsed time and polls status.
* `/contents` returns every stored file at once; it is fetched once per repository visit and cached in memory.
* The guide runs the LLM on every `GET`, so the last result is saved in the browser (`localStorage`) and only
  regenerated on request. Chat transcripts live in `sessionStorage`. Both are cleared on sign-out.
* The assistant sends no history: the backend answers each question independently, and the UI says so.
* Markdown from the model is rendered through React elements (no `innerHTML`); raw HTML is shown as text and only
  `http(s)`/`mailto` links are clickable.

## Known gaps (backend-side)

* No AI-index delete endpoint: deleting a repository removes it in Repository Service, but its AI index remains.
* `/contents` has no file size and the AI status endpoint returns raw column names (`file_count`, ...); the API layer
  maps these as they are today.
* A small History-API router (`src/router/Router.tsx`) is used instead of react-router to avoid a dependency.
