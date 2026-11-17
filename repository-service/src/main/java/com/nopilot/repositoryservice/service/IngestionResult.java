package com.nopilot.repositoryservice.service;

import java.util.List;

/** Everything a successful ingestion produced, before it is saved. */
public record IngestionResult(
        String defaultBranch,
        List<SourceFile> files,
        int skippedFileCount
) {
}
