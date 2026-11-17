package com.nopilot.repositoryservice.service;

/**
 * A downloaded file: this is the unit that will later be handed to the AI Service
 * (path for context, language for chunking, content for embedding).
 */
public record SourceFile(String path, String language, long size, String content) {
}
