package com.nopilot.repositoryservice.dto.response;

/** One ingested file, without its content. */
public record RepositoryFileResponse(String path, long size, String language) {
}
