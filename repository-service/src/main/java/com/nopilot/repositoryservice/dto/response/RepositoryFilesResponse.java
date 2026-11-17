package com.nopilot.repositoryservice.dto.response;

import java.util.List;
import java.util.UUID;

public record RepositoryFilesResponse(
        UUID repositoryId,
        String defaultBranch,
        int fileCount,
        long totalSizeBytes,
        List<RepositoryFileResponse> files
) {
}
