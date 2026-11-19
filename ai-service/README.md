# NoPilot AI Service

Port: `8083`  
Database: `nopilot_ai`  
Runtime: Java 17 / Spring Boot 4.0.9 / Spring AI 2.0.2

## Responsibilities

- Verifies repository ownership through Repository Service using the caller's JWT.
- Reads already-downloaded source contents from Repository Service.
- Filters and chunks source files and documentation.
- Generates embeddings through Spring AI's `EmbeddingModel` backed by Ollama.
- Stores and searches vectors through Spring AI's `PgVectorStore` backed by PostgreSQL + pgvector.
- Uses Spring AI's `ChatClient` backed by Ollama for RAG answers and onboarding guides.
- Keeps vector search and deletion scoped to both repository ID and owner email.
- Explains existing code; it does not generate implementation code or modify source repositories.

## Dependencies

Spring AI 2.0.2 is used with Spring Boot 4.0.9. The official Spring AI Ollama and PGvector starters provide the model and vector-store integrations. No custom Ollama HTTP client or hand-written vector SQL is used.

## Local prerequisites

1. PostgreSQL with the pgvector extension available to the database user.
2. Create database `nopilot_ai`.
3. Ollama running locally.
4. Pull the configured models:

```powershell
ollama pull qwen2.5:3b
ollama pull nomic-embed-text
```

The configured embedding model must produce 768-dimensional vectors. If you change the embedding model or its dimensions, update `spring.ai.vectorstore.pgvector.dimensions`, recreate the `nopilot_code_vectors` table, and re-index repositories.

## Environment variables

Configure these in the IntelliJ Run Configuration:

- `DB_USERNAME` (defaults to `postgres`)
- `DB_PASSWORD`
- `JWT_SECRET` (the same Base64 secret used by User Service and Repository Service)
- `OLLAMA_BASE_URL` (optional; defaults to `http://localhost:11434`)
- `OLLAMA_CHAT_MODEL` (optional; defaults to `qwen2.5:3b`)
- `OLLAMA_EMBEDDING_MODEL` (optional; defaults to `nomic-embed-text`)

Never commit real credentials or `.env` files.

## Startup order

1. PostgreSQL
2. Ollama
3. User Service (`8081`)
4. Repository Service (`8082`)
5. AI Service (`8083`)

The AI Service creates its indexing-status table from `src/main/resources/schema.sql`. Spring AI's PGvector integration creates its own `nopilot_code_vectors` table when `initialize-schema` is enabled. The PostgreSQL server must have pgvector installed; the configured database role needs permission to create the extension/table on first startup.

## API

All endpoints except `/actuator/health` require `Authorization: Bearer <JWT>`.

### 1. Index a repository

First, register and ingest a GitHub repository through Repository Service. Its status must be `READY`.

`POST http://localhost:8083/api/ai/repositories/{repositoryId}/ingest`

The AI Service retrieves stored file contents through Repository Service's authenticated `/api/repositories/{id}/contents` endpoint, filters and chunks them, then stores Spring AI documents and embeddings in PGvector. Returns `READY`, accepted file count and chunk count.

### 2. Check AI index status

`GET http://localhost:8083/api/ai/repositories/{repositoryId}/status`

### 3. Ask a question

`POST http://localhost:8083/api/ai/repositories/{repositoryId}/chat`

```json
{
  "question": "Where is the application entry point and how does startup work?"
}
```

The response contains an answer and source references with file paths and line ranges. Questions requesting code generation or modification are rejected by policy.

### 4. Generate onboarding guide

`GET http://localhost:8083/api/ai/repositories/{repositoryId}/guide`

The guide has exactly three sections: Purpose, Main execution flow, and How the components interact.

## Important behaviour

- AI indexing is separate from GitHub ingestion.
- Repository ownership is re-checked by Repository Service when AI Service fetches source contents.
- Vector documents include repository ID and owner email metadata; retrieval and deletion filter on both values.
- Re-indexing replaces the prior vectors for that repository/owner.
- Chat and guide requests require AI index status `READY`.
- Ingestion is synchronous for this milestone. Background queues are intentionally not introduced yet.
