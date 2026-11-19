package com.nopilot.repositoryservice.service;

import com.nopilot.repositoryservice.config.IngestionProperties;
import com.nopilot.repositoryservice.exception.ApiException;
import com.nopilot.repositoryservice.exception.FileDownloadException;
import com.nopilot.repositoryservice.exception.GitHubApiException;
import com.nopilot.repositoryservice.exception.GitHubNotFoundException;
import com.nopilot.repositoryservice.exception.GitHubRateLimitException;
import com.nopilot.repositoryservice.exception.IngestionFailedException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Talks to GitHub. Read-only: it only issues GET requests, so it can never
 * modify the repository it reads.
 *
 * - Metadata, the file tree, and file blobs come from the REST API (api.github.com).
 * - File contents are fetched by Git blob SHA, avoiding raw.githubusercontent.com.
 *
 * All HTTP/network failures are translated into our own exceptions so that
 * callers never see Spring's RestClient exceptions.
 */
@Service
public class GitHubClient {

    /** Regular files only: 100644 normal, 100755 executable. Skips symlinks (120000). */
    private static final Set<String> REGULAR_FILE_MODES = Set.of("100644", "100755");

    private final RestClient apiClient;
    private final long maxFileSizeBytes;

    public GitHubClient(
            @Value("${nopilot.github.base-url}") String apiBaseUrl,
            @Value("${nopilot.github.token:}") String token,
            @Value("${nopilot.github.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${nopilot.github.read-timeout-seconds:20}") int readTimeoutSeconds,
            IngestionProperties ingestionProperties
    ) {
        JdkClientHttpRequestFactory requestFactory =
                createRequestFactory(connectTimeoutSeconds, readTimeoutSeconds);

        this.apiClient = baseBuilder(requestFactory, token)
                .baseUrl(apiBaseUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();

        this.maxFileSizeBytes = ingestionProperties.getMaxFileSizeBytes();
    }

    // ------------------------------------------------------------------
    // Public operations
    // ------------------------------------------------------------------

    public JsonNode fetchRepositoryMetadata(String owner, String repoName) {

        return execute(
                "repository " + owner + "/" + repoName,
                () -> apiClient.get()
                        .uri("/repos/{owner}/{repo}", owner, repoName)
                        .retrieve()
                        .body(JsonNode.class)
        );
    }

    /**
     * Reads the default branch from metadata that was already fetched,
     * so no second API call is needed. Never assumes "main" or "master".
     */
    public String defaultBranchOf(JsonNode metadata) {

        JsonNode branch = metadata.get("default_branch");

        if (branch == null || branch.isNull() || branch.asText().isBlank()) {
            throw new GitHubApiException(
                    "GitHub did not report a default branch for this repository");
        }

        return branch.asText();
    }

    public GitTree fetchFileTree(String owner, String repoName, String branch) {

        JsonNode root = execute(
                "file tree of branch '" + branch + "'",
                () -> apiClient.get()
                        .uri("/repos/{owner}/{repo}/git/trees/{branch}?recursive=1",
                                owner, repoName, branch)
                        .retrieve()
                        .body(JsonNode.class)
        );

        JsonNode tree = root.get("tree");

        if (tree == null || !tree.isArray()) {
            throw new GitHubApiException("GitHub returned an unexpected file tree response");
        }

        List<TreeEntry> entries = new ArrayList<>();

        for (JsonNode node : tree) {
            if (isRegularFile(node)) {
                entries.add(new TreeEntry(
                        node.path("path").asText(),
                        node.path("size").asLong(0L),
                        node.path("sha").asText()
                ));
            }
        }

        boolean truncated = root.path("truncated").asBoolean(false);

        return new GitTree(entries, truncated);
    }

    /**
     * Downloads a file using the Git Blobs API and the SHA returned by the
     * recursive tree endpoint. This avoids raw.githubusercontent.com entirely.
     *
     * @throws FileDownloadException      this one file failed (caller may skip it)
     * @throws GitHubRateLimitException   limit reached (caller should stop entirely)
     */
    public byte[] downloadFile(String owner, String repoName, TreeEntry entry) {

        if (entry.sha() == null || entry.sha().isBlank()) {
            throw new FileDownloadException(
                    "GitHub did not provide a blob SHA for '" + entry.path() + "'");
        }

        JsonNode blob;

        try {
            blob = apiClient.get()
                    .uri("/repos/{owner}/{repo}/git/blobs/{sha}",
                            owner, repoName, entry.sha())
                    .retrieve()
                    .body(JsonNode.class);

        } catch (RestClientResponseException exception) {
            if (isRateLimited(exception)) {
                throw rateLimitException(exception);
            }
            throw new FileDownloadException(
                    "Failed to download '" + entry.path() + "' (GitHub HTTP "
                            + exception.getStatusCode().value() + ")", exception);

        } catch (RestClientException exception) {
            throw new FileDownloadException(
                    "Failed to download '" + entry.path() + "': " + exception.getMessage(),
                    exception);
        }

        if (blob == null) {
            throw new FileDownloadException(
                    "GitHub returned no content for '" + entry.path() + "'");
        }

        String encoding = blob.path("encoding").asText();
        String content = blob.path("content").asText();

        if (!"base64".equalsIgnoreCase(encoding) || content.isBlank()) {
            throw new FileDownloadException(
                    "GitHub returned an unsupported or empty blob for '" + entry.path() + "'");
        }

        final byte[] body;
        try {
            body = Base64.getMimeDecoder().decode(content);
        } catch (IllegalArgumentException exception) {
            throw new FileDownloadException(
                    "GitHub returned invalid Base64 content for '" + entry.path() + "'",
                    exception);
        }

        if (body.length > maxFileSizeBytes) {
            throw new FileDownloadException(
                    "'" + entry.path() + "' is " + body.length + " bytes, above the limit of "
                            + maxFileSizeBytes);
        }

        return body;
    }

    // ------------------------------------------------------------------
    // Error translation
    // ------------------------------------------------------------------

    /** Runs a GitHub API call and converts every failure into one of our exceptions. */
    private <T> T execute(String target, Supplier<T> call) {

        T result;

        try {
            result = call.get();

        } catch (RestClientResponseException exception) {
            throw translate(target, exception);

        } catch (RestClientException exception) {
            // Timeouts, connection refused, DNS failure, unreadable body...
            throw new GitHubApiException(
                    "Could not fetch " + target + " from GitHub: " + exception.getMessage(),
                    exception);
        }

        if (result == null) {
            throw new GitHubApiException("GitHub returned an empty response for " + target);
        }

        return result;
    }

    private ApiException translate(String target, RestClientResponseException exception) {

        int status = exception.getStatusCode().value();

        if (isRateLimited(exception)) {
            return rateLimitException(exception);
        }

        if (status == 404) {
            return new GitHubNotFoundException(
                    "GitHub could not find the " + target
                            + ". It may not exist, may be private, or the branch may be unavailable");
        }

        if (status == 409) {
            return new IngestionFailedException(
                    "The GitHub repository is empty (it has no commits)");
        }

        if (status == 401) {
            return new GitHubApiException(
                    "GitHub rejected the configured GITHUB_TOKEN (HTTP 401)", exception);
        }

        return new GitHubApiException(
                "GitHub returned HTTP " + status + " while fetching " + target, exception);
    }

    /**
     * GitHub signals rate limiting with 429, or with 403 plus either
     * "X-RateLimit-Remaining: 0", a Retry-After header (secondary limit),
     * or a message mentioning the rate limit.
     */
    private boolean isRateLimited(RestClientResponseException exception) {

        int status = exception.getStatusCode().value();

        if (status == 429) {
            return true;
        }

        if (status != 403) {
            return false;
        }

        HttpHeaders headers = exception.getResponseHeaders();

        if (headers != null
                && ("0".equals(headers.getFirst("X-RateLimit-Remaining"))
                || headers.getFirst(HttpHeaders.RETRY_AFTER) != null)) {
            return true;
        }

        return exception.getResponseBodyAsString()
                .toLowerCase(Locale.ROOT)
                .contains("rate limit");
    }

    private GitHubRateLimitException rateLimitException(RestClientResponseException exception) {

        Long retryAfter = retryAfterSeconds(exception.getResponseHeaders());

        String wait = retryAfter != null
                ? "Retry in about " + retryAfter + " seconds"
                : "Try again later";

        return new GitHubRateLimitException(
                "GitHub rate limit reached. " + wait
                        + ", or set GITHUB_TOKEN to get a higher limit.",
                retryAfter
        );
    }

    private Long retryAfterSeconds(HttpHeaders headers) {

        if (headers == null) {
            return null;
        }

        Long retryAfter = parseLong(headers.getFirst(HttpHeaders.RETRY_AFTER));

        if (retryAfter != null) {
            return retryAfter;
        }

        Long resetEpoch = parseLong(headers.getFirst("X-RateLimit-Reset"));

        if (resetEpoch != null) {
            return Math.max(0, resetEpoch - Instant.now().getEpochSecond());
        }

        return null;
    }

    private Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean isRegularFile(JsonNode node) {
        return "blob".equals(node.path("type").asText())
                && REGULAR_FILE_MODES.contains(node.path("mode").asText());
    }

    private JdkClientHttpRequestFactory createRequestFactory(
            int connectTimeoutSeconds,
            int readTimeoutSeconds
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL) // renamed repos answer with 301
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return factory;
    }

    private RestClient.Builder baseBuilder(
            JdkClientHttpRequestFactory requestFactory,
            String token
    ) {
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "NoPilot-RepositoryService");

        if (token != null && !token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim());
        }

        return builder;
    }
}
