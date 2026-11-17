package com.nopilot.repositoryservice.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown both when the repository does not exist and when it belongs to
 * another user. Using the same 404 for both avoids leaking which IDs exist.
 */
public class RepositoryNotFoundException extends ApiException {

    public RepositoryNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "Repository not found: " + id);
    }
}
