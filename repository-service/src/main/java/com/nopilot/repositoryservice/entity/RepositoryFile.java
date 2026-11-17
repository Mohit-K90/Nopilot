package com.nopilot.repositoryservice.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * One downloaded source file of a repository.
 *
 * The repository is referenced by plain ID (no JPA relation) to keep bulk
 * deletes simple. IngestionStore and RepositoryService are responsible for
 * deleting these rows together with their repository.
 *
 * Note: "content" is TEXT, deliberately NOT @Lob (on PostgreSQL @Lob on a
 * String maps to an "oid" large object, which is awkward to query and delete).
 */
@Entity
@Table(
        name = "repository_files",
        indexes = @Index(name = "idx_repository_files_repository", columnList = "repository_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_repository_files_repository_path",
                columnNames = {"repository_id", "path"}
        )
)
public class RepositoryFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(nullable = false, length = 1000)
    private String path;

    /** Size in bytes (UTF-8) of the stored content. */
    @Column(nullable = false)
    private long size;

    /** JAVA, PYTHON, JAVASCRIPT, TYPESCRIPT, DOCUMENTATION or CONFIG. */
    @Column(nullable = false, length = 30)
    private String language;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public RepositoryFile() {
    }

    public RepositoryFile(
            UUID repositoryId,
            String path,
            long size,
            String language,
            String content
    ) {
        this.repositoryId = repositoryId;
        this.path = path;
        this.size = size;
        this.language = language;
        this.content = content;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public String getLanguage() {
        return language;
    }

    public String getContent() {
        return content;
    }
}
