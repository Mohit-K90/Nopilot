package com.nopilot.repositoryservice.service;

import com.nopilot.repositoryservice.entity.Repository;
import com.nopilot.repositoryservice.entity.RepositoryFile;
import com.nopilot.repositoryservice.entity.RepositoryStatus;
import com.nopilot.repositoryservice.exception.RepositoryNotFoundException;
import com.nopilot.repositoryservice.repository.RepositoryFileRepository;
import com.nopilot.repositoryservice.repository.RepositoryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The database side of ingestion, as short transactions.
 *
 * This is a separate bean from IngestionService on purpose: IngestionService
 * does slow network calls and must NOT hold a database transaction while it
 * does so, and Spring's @Transactional only works when called from another bean.
 *
 * Invariant kept here: a repository has stored files if and only if it is READY.
 */
@Service
public class IngestionStore {

    private static final String INTERRUPTED_REASON =
            "Ingestion was interrupted by a service restart. Please ingest again.";

    private final RepositoryRepository repositoryRepository;
    private final RepositoryFileRepository fileRepository;

    public IngestionStore(
            RepositoryRepository repositoryRepository,
            RepositoryFileRepository fileRepository
    ) {
        this.repositoryRepository = repositoryRepository;
        this.fileRepository = fileRepository;
    }

    /** PENDING/READY/FAILED -> INGESTING. False if it is already INGESTING. */
    @Transactional
    public boolean tryStart(UUID repositoryId) {
        return repositoryRepository.claimForIngestion(
                repositoryId, RepositoryStatus.INGESTING) == 1;
    }

    /** Replaces any old files with the new ones and marks the repository READY, atomically. */
    @Transactional
    public Repository saveSuccess(UUID repositoryId, IngestionResult result) {

        Repository repository = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new RepositoryNotFoundException(repositoryId));

        fileRepository.deleteAllForRepository(repositoryId);
        fileRepository.saveAll(toEntities(repositoryId, result.files()));

        repository.setStatus(RepositoryStatus.READY);
        repository.setDefaultBranch(result.defaultBranch());
        repository.setFileCount(result.files().size());
        repository.setTotalSizeBytes(
                result.files().stream().mapToLong(SourceFile::size).sum());
        repository.setSkippedFileCount(result.skippedFileCount());
        repository.setFailureReason(null);
        repository.setIngestedAt(LocalDateTime.now());

        return repositoryRepository.save(repository);
    }

    /** Marks FAILED and removes any files so FAILED repositories never keep stale data. */
    @Transactional
    public void saveFailure(UUID repositoryId, String reason) {

        fileRepository.deleteAllForRepository(repositoryId);

        repositoryRepository.findById(repositoryId).ifPresent(repository -> {
            repository.setStatus(RepositoryStatus.FAILED);
            repository.setFailureReason(reason);
            repository.setFileCount(null);
            repository.setTotalSizeBytes(null);
            repository.setSkippedFileCount(null);
            repository.setIngestedAt(null);
            repositoryRepository.save(repository);
        });
    }

    /** Returns how many repositories were rescued from a stuck INGESTING state. */
    @Transactional
    public int failInterruptedIngestions() {
        return repositoryRepository.failAllIngesting(
                RepositoryStatus.INGESTING, RepositoryStatus.FAILED, INTERRUPTED_REASON);
    }

    private List<RepositoryFile> toEntities(UUID repositoryId, List<SourceFile> files) {
        return files.stream()
                .map(file -> new RepositoryFile(
                        repositoryId,
                        file.path(),
                        file.size(),
                        file.language(),
                        file.content()))
                .toList();
    }
}
