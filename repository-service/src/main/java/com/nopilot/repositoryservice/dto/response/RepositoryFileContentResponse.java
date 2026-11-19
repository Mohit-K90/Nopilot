package com.nopilot.repositoryservice.dto.response;

import java.util.UUID;

public record RepositoryFileContentResponse(
        UUID repositoryId,
        String path,
        String language,
        String content
) {
}