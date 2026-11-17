package com.nopilot.repositoryservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for every error we deliberately report to API clients.
 * Each subclass fixes its own HTTP status; GlobalExceptionHandler does the rest.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
