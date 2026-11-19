package com.nopilot.repositoryservice.service;

import com.nopilot.repositoryservice.dto.response.RepositoryResponse;
import com.nopilot.repositoryservice.entity.Repository;
import com.nopilot.repositoryservice.exception.ApiException;
import com.nopilot.repositoryservice.exception.FileDownloadException;
import com.nopilot.repositoryservice.exception.IngestionFailedException;
import com.nopilot.repositoryservice.exception.RepositoryStateException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs the ingestion workflow:
 *
 *   PENDING --> INGESTING --> READY
 *                   \------> FAILED
 *
 * It runs synchronously inside the HTTP request for now. To go asynchronous
 * later (RabbitMQ), the controller would publish a message and a listener would
 * call runIngestion(...); the steps themselves do not need to change. The place
 * to notify the AI Service is right after ingestionStore.saveSuccess(...).
 *
 * This class is intentionally NOT @Transactional: it makes slow network calls,
 * and every database step is a short transaction inside IngestionStore.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private static final int MAX_REASON_LENGTH = 1000;

    private final RepositoryService repositoryService;
    private final IngestionStore ingestionStore;
    private final GitHubClient gitHubClient;
    private final SourceFileFilter sourceFileFilter;

    public IngestionService(
            RepositoryService repositoryService,
            IngestionStore ingestionStore,
            GitHubClient gitHubClient,
            SourceFileFilter sourceFileFilter
    ) {
        this.repositoryService = repositoryService;
        this.ingestionStore = ingestionStore;
        this.gitHubClient = gitHubClient;
        this.sourceFileFilter = sourceFileFilter;
    }

    /**
     * If the service was killed mid-ingestion, those repositories would stay
     * INGESTING forever. Nothing can be running at startup, so mark them FAILED.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedIngestions() {
        int recovered = ingestionStore.failInterruptedIngestions();

        if (recovered > 0) {
            log.warn("Marked {} interrupted ingestion(s) as FAILED", recovered);
        }
    }

    public RepositoryResponse ingest(UUID repositoryId, String userEmail) {

        // 404 if it does not exist or belongs to someone else.
        Repository repository = repositoryService.getOwnedRepository(repositoryId, userEmail);

        // Atomic claim: only one caller can move it to INGESTING.
        if (!ingestionStore.tryStart(repositoryId)) {
            throw new RepositoryStateException(
                    "Ingestion is already in progress for this repository");
        }

        return runIngestion(repository);
    }

    // ------------------------------------------------------------------

    private RepositoryResponse runIngestion(Repository repository) {

        UUID repositoryId = repository.getId();

        try {
            IngestionResult result = fetchAndPrepare(repository);
            Repository saved = ingestionStore.saveSuccess(repositoryId, result);

            log.info("Ingested {}/{}: {} files, {} skipped",
                    repository.getOwner(), repository.getRepoName(),
                    result.files().size(), result.skippedFileCount());

            return RepositoryResponse.from(saved);

        } catch (RuntimeException exception) {
            // Whatever went wrong, never leave the repository INGESTING.
            markFailed(repositoryId, exception);
            throw exception;
        }
    }

    private IngestionResult fetchAndPrepare(Repository repository) {

        String owner = repository.getOwner();
        String repoName = repository.getRepoName();

        JsonNode metadata = gitHubClient.fetchRepositoryMetadata(owner, repoName);
        String branch = gitHubClient.defaultBranchOf(metadata);

        GitTree tree = gitHubClient.fetchFileTree(owner, repoName, branch);

        if (tree.truncated()) {
            throw new IngestionFailedException(
                    "Repository is too large: GitHub could not return its complete file tree");
        }

        SourceFileFilter.Selection selection = sourceFileFilter.select(tree.entries());

        if (selection.files().isEmpty()) {
            throw new IngestionFailedException(
                    "No relevant source files found within the configured size limits");
        }

        List<SourceFile> files = downloadFiles(owner, repoName, branch, selection.files());

        if (files.isEmpty()) {
            throw new IngestionFailedException(
                    "None of the " + selection.files().size()
                            + " selected files could be downloaded");
        }

        return new IngestionResult(
                branch,
                files,
                selection.relevantCount() - files.size()
        );
    }

    /**
     * Downloads sequentially. A failing single file is skipped; a rate limit
     * (a different exception type) aborts the whole ingestion.
     */
    private List<SourceFile> downloadFiles(
            String owner,
            String repoName,
            String branch,
            List<TreeEntry> entries
    ) {
        List<SourceFile> files = new ArrayList<>();

        for (TreeEntry entry : entries) {
            try {
                byte[] bytes = gitHubClient.downloadFile(owner, repoName, entry);

                Optional<String> text = decodeText(bytes);

                if (text.isPresent()) {
                    files.add(new SourceFile(
                            entry.path(),
                            sourceFileFilter.languageOf(entry.path()),
                            bytes.length,
                            text.get()));
                } else {
                    log.info("Skipping non-text file {}", entry.path());
                }

            } catch (FileDownloadException exception) {
                log.warn("Skipping {}: {}", entry.path(), exception.getMessage());
            }
        }

        return files;
    }

    /**
     * Accepts only valid UTF-8 without NUL bytes. Anything else is treated as
     * binary (and PostgreSQL cannot store NUL characters in text anyway).
     */
    private Optional<String> decodeText(byte[] bytes) {

        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            String text = decoder.decode(ByteBuffer.wrap(bytes)).toString();

            if (text.indexOf('\0') >= 0) {
                return Optional.empty();
            }

            if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
                text = text.substring(1); // strip byte-order mark
            }

            return Optional.of(text);

        } catch (CharacterCodingException exception) {
            return Optional.empty();
        }
    }

    private void markFailed(UUID repositoryId, RuntimeException cause) {

        String reason = cause instanceof ApiException
                ? cause.getMessage()
                : "Unexpected error during ingestion (" + cause.getClass().getSimpleName() + ")";

        if (reason.length() > MAX_REASON_LENGTH) {
            reason = reason.substring(0, MAX_REASON_LENGTH);
        }

        try {
            ingestionStore.saveFailure(repositoryId, reason);
        } catch (RuntimeException storeException) {
            // E.g. database down. Startup recovery will clean up a stuck INGESTING row.
            log.error("Could not record ingestion failure for {}", repositoryId, storeException);
        }
    }
}
