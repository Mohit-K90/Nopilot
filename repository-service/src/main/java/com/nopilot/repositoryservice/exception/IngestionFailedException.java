package com.nopilot.repositoryservice.exception;

import org.springframework.http.HttpStatus;

public class IngestionFailedException extends ApiException {

    public IngestionFailedException(String message) {
        super(HttpStatus.valueOf(422), message);
    }
}
