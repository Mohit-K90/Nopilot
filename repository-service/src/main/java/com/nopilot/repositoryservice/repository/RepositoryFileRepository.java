package com.nopilot.repositoryservice.repository;

import com.nopilot.repositoryservice.entity.RepositoryFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface RepositoryFileRepository extends JpaRepository<RepositoryFile, UUID> {

    /** Projection: lists files WITHOUT loading their (large) content column. */
    interface FileSummary {
        String getPath();

        Long getSize();

        String getLanguage();
    }

    List<FileSummary> findSummariesByRepositoryIdOrderByPathAsc(UUID repositoryId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RepositoryFile f where f.repositoryId = :repositoryId")
    int deleteAllForRepository(@Param("repositoryId") UUID repositoryId);
}
