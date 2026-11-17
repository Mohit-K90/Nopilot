# Claude AI Service progress snapshot

This folder contains the AI Service scaffold from the original NoPilot ZIP plus 16 Java source files reconstructed from the Claude shell commands shared in ChatGPT.

## Included work
- AiProperties configuration model
- FileHasher and LanguageDetector utilities
- AiFileFilter
- CodeChunk, RetrievedChunk, ContextChunk, TokenUsage, LlmResult, FileInfo, GuideFacts models
- ChunkingService, ContextBuilder, PromptBuilder, RequestPolicy, FactsExtractor

## Not yet present in the supplied Claude output
- Ollama/Spring AI configuration and model integration
- PGVector configuration and persistence
- Repository Service client and secure source-content retrieval
- Ingestion orchestration and status persistence
- Semantic search API and implementation
- RAG chat API and implementation
- Onboarding guide API and LLM generation
- JWT security implementation for AI Service
- Unit tests for the newly generated classes
- AI Service Postman collection and execution results

## Important
The source files were reconstructed from the exact heredoc contents supplied in the conversation. Claude's own workspace was not directly accessible, so this is a recovery snapshot, not a verified export of its workspace. No Maven build or tests have been run on this snapshot.

The original User Service JWT secret and database password have been replaced with environment-variable placeholders in this copy. The Git history was excluded.
