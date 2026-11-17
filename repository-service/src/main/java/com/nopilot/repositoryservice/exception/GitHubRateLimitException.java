package com.nopilot.repositoryservice.exception;

import org.springframework.http.HttpStatus;

/** GitHub refused the request because the rate limit was reached. */
public class GitHubRateLimitException extends ApiException {

    private final Long retryAfterSeconds;

    public GitHubRateLimitException(String message, Long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** Seconds until the limit resets, or null if GitHub did not say. */
    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
