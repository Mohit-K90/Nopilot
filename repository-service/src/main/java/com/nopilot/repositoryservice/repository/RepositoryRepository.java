package com.nopilot.repositoryservice.repository;

import com.nopilot.repositoryservice.entity.Repository;
import com.nopilot.repositoryservice.entity.RepositoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryRepository extends JpaRepository<Repository, UUID> {

    List<Repository> findByUserEmail(String userEmail);

    List<Repository> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    /** The only way services load a single repository: ownership is part of the query. */
    Optional<Repository> findByIdAndUserEmail(UUID id, String userEmail);

    boolean existsByUserEmailAndGithubUrl(
            String userEmail,
            String githubUrl
    );

    /** GitHub names are case-insensitive, so "Foo/Bar" and "foo/bar" are the same repository. */
    boolean existsByUserEmailAndOwnerIgnoreCaseAndRepoNameIgnoreCase(
            String userEmail,
            String owner,
            String repoName
    );

    /**
     * Atomically moves a repository to INGESTING unless it already is.
     * Returns 1 if this caller won, 0 if another ingestion is running
     * (or the repository does not exist). This prevents double ingestion.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Repository r set r.status = :ingesting, r.failureReason = null "
            + "where r.id = :id and r.status <> :ingesting")
    int claimForIngestion(
            @Param("id") UUID id,
            @Param("ingesting") RepositoryStatus ingesting
    );

    /** Used at startup to rescue repositories left INGESTING by a crash/restart. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Repository r set r.status = :failed, r.failureReason = :reason "
            + "where r.status = :ingesting")
    int failAllIngesting(
            @Param("ingesting") RepositoryStatus ingesting,
            @Param("failed") RepositoryStatus failed,
            @Param("reason") String reason
    );
}
