package com.nopilot.repositoryservice.service;

import com.nopilot.repositoryservice.dto.request.CreateRepositoryRequest;
import com.nopilot.repositoryservice.dto.response.RepositoryFileResponse;
import com.nopilot.repositoryservice.dto.response.RepositoryFilesResponse;
import com.nopilot.repositoryservice.dto.response.RepositoryResponse;
import com.nopilot.repositoryservice.entity.Repository;
import com.nopilot.repositoryservice.entity.RepositoryStatus;
import com.nopilot.repositoryservice.exception.DuplicateRepositoryException;
import com.nopilot.repositoryservice.exception.RepositoryNotFoundException;
import com.nopilot.repositoryservice.exception.RepositoryStateException;
import com.nopilot.repositoryservice.repository.RepositoryFileRepository;
import com.nopilot.repositoryservice.repository.RepositoryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Registration and management of a user's repositories.
 * Every method that touches an existing repository takes the caller's email and
 * loads the repository through it, so another user's repository is simply "not found".
 * (GitHub fetching lives in IngestionService.)
 */
@Service
public class RepositoryService {

    private final RepositoryRepository repositoryRepository;
    private final RepositoryFileRepository fileRepository;

    public RepositoryService(
            RepositoryRepository repositoryRepository,
            RepositoryFileRepository fileRepository
    ) {
        this.repositoryRepository = repositoryRepository;
        this.fileRepository = fileRepository;
    }

    @Transactional
    public Repository registerRepository(
            CreateRepositoryRequest request,
            String userEmail
    ) {
        GitHubUrl gitHubUrl = GitHubUrl.parse(request.getGithubUrl());

        if (repositoryRepository.existsByUserEmailAndOwnerIgnoreCaseAndRepoNameIgnoreCase(
                userEmail, gitHubUrl.owner(), gitHubUrl.repoName()
        )) {
            throw new DuplicateRepositoryException("Repository already registered");
        }

        Repository repository = new Repository();
        repository.setUserEmail(userEmail);
        repository.setGithubUrl(gitHubUrl.canonicalUrl());
        repository.setOwner(gitHubUrl.owner());
        repository.setRepoName(gitHubUrl.repoName());
        repository.setStatus(RepositoryStatus.PENDING);

        return repositoryRepository.save(repository);
    }

    @Transactional(readOnly = true)
    public List<RepositoryResponse> listRepositories(String userEmail) {

        return repositoryRepository.findByUserEmailOrderByCreatedAtDesc(userEmail)
                .stream()
                .map(RepositoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RepositoryResponse getRepository(UUID id, String userEmail) {
        return RepositoryResponse.from(getOwnedRepository(id, userEmail));
    }

    /** File list (no contents). Only meaningful once the repository is READY. */
    @Transactional(readOnly = true)
    public RepositoryFilesResponse getRepositoryFiles(UUID id, String userEmail) {

        Repository repository = getOwnedRepository(id, userEmail);

        if (repository.getStatus() != RepositoryStatus.READY) {
            throw new RepositoryStateException(
                    "Repository has no files yet (status: " + repository.getStatus()
                            + "). Run POST /api/repositories/" + id + "/ingest first");
        }

        List<RepositoryFileResponse> files = fileRepository
                .findSummariesByRepositoryIdOrderByPathAsc(id)
                .stream()
                .map(file -> new RepositoryFileResponse(
                        file.getPath(), file.getSize(), file.getLanguage()))
                .toList();

        long totalSize = files.stream().mapToLong(RepositoryFileResponse::size).sum();

        return new RepositoryFilesResponse(
                id,
                repository.getDefaultBranch(),
                files.size(),
                totalSize,
                files
        );
    }

    /** Deletes the repository registration and its locally stored files. GitHub is never touched. */
    @Transactional
    public void deleteRepository(UUID id, String userEmail) {

        Repository repository = getOwnedRepository(id, userEmail);

        if (repository.getStatus() == RepositoryStatus.INGESTING) {
            throw new RepositoryStateException(
                    "Cannot delete a repository while it is being ingested");
        }

        fileRepository.deleteAllForRepository(id);
        repositoryRepository.delete(repository);
    }

    /** Loads a repository only if it belongs to userEmail; otherwise 404. */
    @Transactional(readOnly = true)
    public Repository getOwnedRepository(UUID id, String userEmail) {

        return repositoryRepository.findByIdAndUserEmail(id, userEmail)
                .orElseThrow(() -> new RepositoryNotFoundException(id));
    }
}
