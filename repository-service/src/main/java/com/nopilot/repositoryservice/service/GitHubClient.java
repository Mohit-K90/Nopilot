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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Talks to GitHub. Read-only: it only issues GET requests, so it can never
 * modify the repository it reads.
 *
 * - Metadata and the file tree come from the REST API (api.github.com).
 * - File contents come from raw.githubusercontent.com, which (unlike the API)
 *   does not eat into the 60 requests/hour unauthenticated API limit.
 *
 * All HTTP/network failures are translated into our own exceptions so that
 * callers never see Spring's RestClient exceptions.
 */
@Service
public class GitHubClient {

    /** Regular files only: 100644 normal, 100755 executable. Skips symlinks (120000). */
    private static final Set<String> REGULAR_FILE_MODES = Set.of("100644", "100755");

    private final RestClient apiClient;
    private final RestClient rawClient;
    private final String rawBaseUrl;
    private final long maxFileSizeBytes;

    public GitHubClient(
            @Value("${nopilot.github.base-url}") String apiBaseUrl,
            @Value("${nopilot.github.raw-base-url}") String rawBaseUrl,
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

        this.rawClient = baseBuilder(requestFactory, token).build();
        this.rawBaseUrl = rawBaseUrl;
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
                        node.path("size").asLong(0L)
                ));
            }
        }

        boolean truncated = root.path("truncated").asBoolean(false);

        return new GitTree(entries, truncated);
    }

    /**
     * Downloads one file's raw bytes.
     *
     * @throws FileDownloadException      this one file failed (caller may skip it)
     * @throws GitHubRateLimitException   limit reached (caller should stop entirely)
     */
    public byte[] downloadFile(String owner, String repoName, String branch, String path) {

        URI uri = UriComponentsBuilder.fromUriString(rawBaseUrl)
                .pathSegment(owner, repoName, branch)
                .pathSegment(path.split("/"))
                .build()
                .encode()
                .toUri();

        byte[] body;

        try {
            body = rawClient.get().uri(uri).retrieve().body(byte[].class);

        } catch (RestClientResponseException exception) {
            if (isRateLimited(exception)) {
                throw rateLimitException(exception);
            }
            throw new FileDownloadException(
                    "Failed to download '" + path + "' (HTTP "
                            + exception.getStatusCode().value() + ")", exception);

        } catch (RestClientException exception) {
            throw new FileDownloadException(
                    "Failed to download '" + path + "': " + exception.getMessage(), exception);
        }

        if (body == null) {
            throw new FileDownloadException("GitHub returned no content for '" + path + "'");
        }

        if (body.length > maxFileSizeBytes) {
            throw new FileDownloadException(
                    "'" + path + "' is " + body.length + " bytes, above the limit of "
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
