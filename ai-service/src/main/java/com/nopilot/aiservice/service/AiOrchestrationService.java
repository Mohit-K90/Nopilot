package com.nopilot.aiservice.service;

import com.nopilot.aiservice.client.RepositoryClient;
import com.nopilot.aiservice.config.AiProperties;
import com.nopilot.aiservice.model.*;
import com.nopilot.aiservice.util.LanguageDetector;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AiOrchestrationService {
    private final RepositoryClient repositoryClient;
    private final ChatClient chatClient;
    private final AiIndexStore indexStore;
    private final AiFileFilter fileFilter;
    private final ChunkingService chunker;
    private final ContextBuilder contextBuilder;
    private final PromptBuilder prompts;
    private final RequestPolicy policy;
    private final FactsExtractor factsExtractor;
    private final AiProperties properties;

    public AiOrchestrationService(
            RepositoryClient repositoryClient,
            ChatClient.Builder chatClientBuilder,
            AiIndexStore indexStore,
            AiFileFilter fileFilter,
            ChunkingService chunker,
            ContextBuilder contextBuilder,
            PromptBuilder prompts,
            RequestPolicy policy,
            FactsExtractor factsExtractor,
            AiProperties properties) {
        this.repositoryClient = repositoryClient;
        this.chatClient = chatClientBuilder.build();
        this.indexStore = indexStore;
        this.fileFilter = fileFilter;
        this.chunker = chunker;
        this.contextBuilder = contextBuilder;
        this.prompts = prompts;
        this.policy = policy;
        this.factsExtractor = factsExtractor;
        this.properties = properties;
    }

    public Map<String, Object> ingest(UUID id, String email, String token) {
        Map<String, Object> repository = repositoryClient.getRepository(id, token);
        if (!"READY".equals(String.valueOf(repository.get("status")))) {
            throw new IllegalStateException("Repository Service must finish GitHub ingestion first (current status: "
                    + repository.get("status") + ")");
        }

        indexStore.begin(id, email);
        int acceptedFiles = 0;
        int totalChunks = 0;
        try {
            List<Map<String, Object>> files = repositoryClient.getFileContents(id, token);
            List<CodeChunk> pending = new ArrayList<>();

            for (Map<String, Object> file : files) {
                String path = Objects.toString(file.get("path"), "");
                String content = Objects.toString(file.get("content"), "");
                if (fileFilter.skipReason(path, content) != null) continue;

                String language = Objects.toString(file.get("language"), LanguageDetector.detect(path));
                List<CodeChunk> chunks = chunker.chunk(path, language, content);
                if (!chunks.isEmpty()) acceptedFiles++;

                for (CodeChunk chunk : chunks) {
                    pending.add(chunk);
                    if (pending.size() >= properties.getIngestion().getEmbeddingBatchSize()) {
                        totalChunks += saveBatch(id, email, pending);
                    }
                }
            }
            if (!pending.isEmpty()) totalChunks += saveBatch(id, email, pending);

            indexStore.complete(id, email, acceptedFiles, totalChunks);
            return Map.of("repositoryId", id, "status", "READY",
                    "fileCount", acceptedFiles, "chunkCount", totalChunks);
        } catch (RuntimeException ex) {
            indexStore.fail(id, email, ex.getMessage());
            throw ex;
        }
    }

    private int saveBatch(UUID repositoryId, String email, List<CodeChunk> pending) {
        List<CodeChunk> batch = List.copyOf(pending);
        indexStore.saveBatch(repositoryId, email, batch);
        pending.clear();
        return batch.size();
    }

    public Map<String, Object> chat(UUID id, String email, String question) {
        ensureReady(id, email);
        if (question == null || question.isBlank()
                || question.length() > properties.getChat().getMaxQuestionChars()) {
            throw new IllegalArgumentException("Question must be between 1 and "
                    + properties.getChat().getMaxQuestionChars() + " characters");
        }
        if (policy.isCodeChangeRequest(question)) {
            return Map.of("repositoryId", id, "answer", RequestPolicy.REFUSAL, "sources", List.of());
        }

        List<RetrievedChunk> retrieved = indexStore.search(id, email, question,
                properties.getChat().getRetrievalTopK(),
                properties.getRetrieval().getSimilarityThreshold());
        List<ContextChunk> context = contextBuilder.build(id, retrieved,
                properties.getChat().getMaxChunksInPrompt(),
                properties.getChat().getMaxChunksPerFile(),
                properties.getChat().getMaxContextChars());

        if (context.isEmpty()) {
            return Map.of("repositoryId", id, "answer", PromptBuilder.INSUFFICIENT_CONTEXT,
                    "sources", List.of());
        }

        String answer = chatClient.prompt()
                .system(prompts.chatSystemPrompt())
                .user(prompts.chatUserPrompt(context, question))
                .call()
                .content();

        List<Map<String, Object>> sources = context.stream().map(chunk -> Map.<String, Object>of(
                "citation", "[" + chunk.number() + "]",
                "filePath", chunk.filePath(),
                "language", chunk.language(),
                "startLine", chunk.startLine(),
                "endLine", chunk.endLine(),
                "score", chunk.score())).toList();

        return Map.of("repositoryId", id, "answer", answer, "sources", sources);
    }

    public Map<String, Object> guide(UUID id, String email, String token) {
        ensureReady(id, email);
        List<Map<String, Object>> rawFiles = repositoryClient.getFileContents(id, token);
        List<FileInfo> infos = new ArrayList<>();
        Map<String, String> keyContents = new LinkedHashMap<>();
        List<String> entryPoints = new ArrayList<>();

        for (Map<String, Object> file : rawFiles) {
            String path = Objects.toString(file.get("path"), "");
            String language = Objects.toString(file.get("language"), LanguageDetector.detect(path));
            String content = Objects.toString(file.get("content"), "");
            Object size = file.getOrDefault("size", content.length());
            infos.add(new FileInfo(path, language, ((Number) size).longValue()));

            String name = LanguageDetector.fileName(path).toLowerCase(Locale.ROOT);
            if (name.startsWith("readme") || name.equals("pom.xml") || name.equals("package.json")
                    || name.equals("build.gradle") || name.equals("pyproject.toml")
                    || name.startsWith("requirements")) {
                keyContents.put(path, content);
            }
            if (name.endsWith("application.java") || name.equals("main.py")
                    || name.equals("index.js") || name.equals("server.js")) {
                entryPoints.add(path);
            }
        }

        GuideFacts facts = factsExtractor.extract(infos, keyContents, entryPoints,
                properties.getGuide().getMaxFilesListed(), properties.getGuide().getMaxDependencies());
        String factsText = "Languages: " + facts.languages()
                + "\nTechnologies: " + facts.technologies()
                + "\nFolders: " + facts.folderStructure()
                + "\nEntry points: " + facts.entryPoints()
                + "\nImportant files: " + facts.importantFiles()
                + "\nDependencies: " + facts.dependencies()
                + "\nHow to run: " + facts.howToRun();

        String retrievalQuery = "Explain this repository's purpose, main execution flow and component interactions";
        List<RetrievedChunk> retrieved = indexStore.search(id, email, retrievalQuery,
                properties.getChat().getRetrievalTopK(),
                properties.getRetrieval().getSimilarityThreshold());
        List<ContextChunk> context = contextBuilder.build(id, retrieved,
                properties.getChat().getMaxChunksInPrompt(),
                properties.getChat().getMaxChunksPerFile(),
                properties.getGuide().getMaxContextChars());

        String guide = chatClient.prompt()
                .system(prompts.guideSystemPrompt())
                .user(prompts.guideUserPrompt(factsText, context))
                .call()
                .content();

        return Map.of("repositoryId", id, "guide", guide, "facts", facts);
    }

    public Map<String, Object> status(UUID id, String email) {
        return indexStore.status(id, email);
    }

    private void ensureReady(UUID id, String email) {
        Map<String, Object> status = indexStore.status(id, email);
        if (!"READY".equals(String.valueOf(status.get("status")))) {
            throw new IllegalStateException("AI index is not READY (status: " + status.get("status") + ")");
        }
    }
}
