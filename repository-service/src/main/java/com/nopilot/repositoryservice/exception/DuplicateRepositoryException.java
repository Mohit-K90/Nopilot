package com.nopilot.repositoryservice.exception;

import org.springframework.http.HttpStatus;

public class DuplicateRepositoryException extends ApiException {

    public DuplicateRepositoryException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
