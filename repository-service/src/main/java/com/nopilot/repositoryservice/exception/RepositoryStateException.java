package com.nopilot.repositoryservice.exception;

import org.springframework.http.HttpStatus;

public class RepositoryStateException extends ApiException {

    public RepositoryStateException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
