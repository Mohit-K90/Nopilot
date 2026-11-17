package com.nopilot.repositoryservice.exception;

import org.springframework.http.HttpStatus;

/**
 * A single file could not be downloaded (HTTP error, timeout, too large...).
 * Ingestion catches this per file and skips the file instead of failing everything.
 */
public class FileDownloadException extends ApiException {

    public FileDownloadException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }

    public FileDownloadException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message, cause);
    }
}
