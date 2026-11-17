package com.nopilot.repositoryservice.service;

import com.nopilot.repositoryservice.exception.InvalidGitHubUrlException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed GitHub repository URL.
 *
 * Owner and repo name are restricted to the characters GitHub allows, because
 * they are later placed into API paths.
 */
public record GitHubUrl(String owner, String repoName) {

    private static final Pattern PATTERN = Pattern.compile(
            "^https://github\\.com/"
                    + "([A-Za-z0-9][A-Za-z0-9-]{0,38})/"
                    + "([A-Za-z0-9._-]{1,100})/?$"
    );

    private static final String SUFFIX = ".git";

    public static GitHubUrl parse(String rawUrl) {

        if (rawUrl == null) {
            throw new InvalidGitHubUrlException("GitHub URL is required");
        }

        Matcher matcher = PATTERN.matcher(rawUrl.trim());

        if (!matcher.matches()) {
            throw new InvalidGitHubUrlException(
                    "Invalid GitHub URL. Expected https://github.com/{owner}/{repo}");
        }

        String owner = matcher.group(1);
        String repoName = matcher.group(2);

        if (repoName.endsWith(SUFFIX)) {
            repoName = repoName.substring(0, repoName.length() - SUFFIX.length());
        }

        if (repoName.isEmpty() || repoName.equals(".") || repoName.equals("..")) {
            throw new InvalidGitHubUrlException(
                    "Invalid GitHub URL. Expected https://github.com/{owner}/{repo}");
        }

        return new GitHubUrl(owner, repoName);
    }

    /** Normalised form stored in the database (no ".git", no trailing slash). */
    public String canonicalUrl() {
        return "https://github.com/" + owner + "/" + repoName;
    }
}
