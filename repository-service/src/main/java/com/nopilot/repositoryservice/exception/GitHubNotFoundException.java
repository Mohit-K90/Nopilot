package com.nopilot.repositoryservice.exception;

import org.springframework.http.HttpStatus;

public class GitHubNotFoundException extends ApiException {

    public GitHubNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
