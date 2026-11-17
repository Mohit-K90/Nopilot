package com.nopilot.repositoryservice.dto.response;

import com.nopilot.repositoryservice.entity.Repository;
import com.nopilot.repositoryservice.entity.RepositoryStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/** API view of a repository. The JPA entity is never returned directly. */
public record RepositoryResponse(
        UUID id,
        String githubUrl,
        String owner,
        String repoName,
        RepositoryStatus status,
        String defaultBranch,
        Integer fileCount,
        Long totalSizeBytes,
        Integer skippedFileCount,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime ingestedAt
) {

    public static RepositoryResponse from(Repository repository) {
        return new RepositoryResponse(
                repository.getId(),
                repository.getGithubUrl(),
                repository.getOwner(),
                repository.getRepoName(),
                repository.getStatus(),
                repository.getDefaultBranch(),
                repository.getFileCount(),
                repository.getTotalSizeBytes(),
                repository.getSkippedFileCount(),
                repository.getFailureReason(),
                repository.getCreatedAt(),
                repository.getIngestedAt()
        );
    }
}
