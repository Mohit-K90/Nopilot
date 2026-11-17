package com.nopilot.repositoryservice.exception;

import org.springframework.http.HttpStatus;

/** GitHub was unreachable, timed out, or answered with an unexpected error. */
public class GitHubApiException extends ApiException {

    public GitHubApiException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }

    public GitHubApiException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message, cause);
    }
}
