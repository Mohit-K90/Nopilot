package com.nopilot.repositoryservice.exception;

import org.springframework.http.HttpStatus;

public class InvalidGitHubUrlException extends ApiException {

    public InvalidGitHubUrlException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
