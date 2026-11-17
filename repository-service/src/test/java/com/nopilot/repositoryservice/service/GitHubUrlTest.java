package com.nopilot.repositoryservice.service;

import com.nopilot.repositoryservice.exception.InvalidGitHubUrlException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubUrlTest {

    @Test
    void parsesOwnerAndRepository() {
        GitHubUrl url = GitHubUrl.parse("https://github.com/spring-projects/spring-boot");

        assertThat(url.owner()).isEqualTo("spring-projects");
        assertThat(url.repoName()).isEqualTo("spring-boot");
    }

    @Test
    void normalisesGitSuffixTrailingSlashAndWhitespace() {
        GitHubUrl url = GitHubUrl.parse("  https://github.com/a/b.git/ ");

        assertThat(url.canonicalUrl()).isEqualTo("https://github.com/a/b");
    }

    @Test
    void keepsDotsInRepositoryNames() {
        assertThat(GitHubUrl.parse("https://github.com/socketio/socket.io").repoName())
                .isEqualTo("socket.io");
    }

    @Test
    void rejectsUrlsThatAreNotPlainRepositoryUrls() {
        String[] invalid = {
                "http://github.com/a/b",
                "https://gitlab.com/a/b",
                "https://github.com/a",
                "https://github.com/a/b/tree/main",
                "https://github.com/a/..",
                "https://github.com/a/.git",
                "https://github.com/-a/b",
                "https://github.com/a/b?x=1",
                ""
        };

        for (String url : invalid) {
            assertThatThrownBy(() -> GitHubUrl.parse(url))
                    .as(url)
                    .isInstanceOf(InvalidGitHubUrlException.class);
        }
    }
}
