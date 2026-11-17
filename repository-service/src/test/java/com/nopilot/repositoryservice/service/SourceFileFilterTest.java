package com.nopilot.repositoryservice.service;

import com.nopilot.repositoryservice.config.IngestionProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceFileFilterTest {

    private final SourceFileFilter filter = new SourceFileFilter(new IngestionProperties());

    @Test
    void keepsSourceReadmeAndConfigurationFiles() {
        String[] relevant = {
                "src/Main.java", "app.py", "web/App.tsx", "x.ts", "a/b/c.js",
                "README.md", "readme", "sub/module/README.zh-CN.md",
                "pom.xml", "package.json", "Dockerfile",
                "src/main/resources/application-dev.properties",
                "docs/guide.md"
        };

        for (String path : relevant) {
            assertThat(filter.isRelevant(path)).as(path).isTrue();
        }
    }

    @Test
    void ignoresGeneratedBinaryAndDependencyFiles() {
        String[] ignored = {
                ".git/config", "node_modules/lib/index.js", "a/node_modules/b/c.js",
                "target/classes/A.class", "build/out.js", "dist/bundle.js",
                "venv/lib/x.py", "__pycache__/x.py",
                "logo.png", "readme.png", "lib.jar", "data.csv",
                "app.min.js", "types/index.d.ts", "app.js.map",
                "package-lock.json", "LICENSE", "CHANGELOG.md", "notes/todo.md"
        };

        for (String path : ignored) {
            assertThat(filter.isRelevant(path)).as(path).isFalse();
        }
    }

    @Test
    void appliesCountAndSizeLimitsBestFilesFirst() {
        IngestionProperties limits = new IngestionProperties();
        limits.setMaxFiles(3);
        limits.setMaxFileSizeBytes(1000);
        limits.setMaxTotalSizeBytes(2000);
        SourceFileFilter limited = new SourceFileFilter(limits);

        SourceFileFilter.Selection selection = limited.select(List.of(
                new TreeEntry("src/deep/er/Z.java", 500),
                new TreeEntry("src/A.java", 500),
                new TreeEntry("README.md", 100),
                new TreeEntry("pom.xml", 200),
                new TreeEntry("big.java", 5000),
                new TreeEntry("empty.py", 0),
                new TreeEntry("logo.png", 10)
        ));

        assertThat(selection.files()).extracting(TreeEntry::path)
                .containsExactly("README.md", "pom.xml", "src/A.java");
        assertThat(selection.relevantCount()).isEqualTo(6); // everything except logo.png
    }

    @Test
    void labelsLanguages() {
        assertThat(filter.languageOf("a/B.java")).isEqualTo("JAVA");
        assertThat(filter.languageOf("a/B.tsx")).isEqualTo("TYPESCRIPT");
        assertThat(filter.languageOf("README.md")).isEqualTo("DOCUMENTATION");
        assertThat(filter.languageOf("pom.xml")).isEqualTo("CONFIG");
    }
}
