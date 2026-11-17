package com.nopilot.repositoryservice.service;

import com.nopilot.repositoryservice.config.IngestionProperties;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides which files of a repository are worth downloading.
 *
 * Two steps:
 *   1. isRelevant(path)  - path rules only (extension / name / directory).
 *   2. select(entries)   - applies size and count limits to the relevant files,
 *                          best files first, so the limits cut the least useful ones.
 */
@Component
public class SourceFileFilter {

    private static final int MAX_PATH_LENGTH = 1000;

    /** Result of select(): the files to download and how many relevant files existed. */
    public record Selection(List<TreeEntry> files, int relevantCount) {
    }

    /** Any directory with one of these names (at any depth) is ignored entirely. */
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".svn", ".hg",
            "node_modules", "bower_components", "vendor",
            "target", "build", "dist", "out",
            ".gradle", ".mvn", ".idea", ".vscode",
            "venv", ".venv", "__pycache__", ".tox", ".pytest_cache", ".mypy_cache", "site-packages",
            "coverage", ".next", ".nuxt"
    );

    /** Generated / bundled files that share an allowed extension. */
    private static final List<String> GENERATED_SUFFIXES = List.of(
            ".min.js", ".bundle.js", ".chunk.js", ".d.ts"
    );

    private static final Map<String, String> LANGUAGE_BY_EXTENSION = Map.of(
            "java", "JAVA",
            "py", "PYTHON",
            "js", "JAVASCRIPT",
            "jsx", "JAVASCRIPT",
            "ts", "TYPESCRIPT",
            "tsx", "TYPESCRIPT"
    );

    private static final Set<String> DOC_EXTENSIONS =
            Set.of("md", "markdown", "rst", "txt", "adoc");

    /** Build / dependency / runtime configuration that explains how a project is put together. */
    private static final Set<String> CONFIG_FILE_NAMES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts",
            "settings.gradle", "settings.gradle.kts",
            "package.json", "tsconfig.json",
            "pyproject.toml", "setup.cfg", "pipfile",
            "dockerfile", "docker-compose.yml", "docker-compose.yaml",
            "makefile", ".env.example",
            "contributing.md", "architecture.md"
    );

    private final IngestionProperties properties;

    public SourceFileFilter(IngestionProperties properties) {
        this.properties = properties;
    }

    // ------------------------------------------------------------------

    public boolean isRelevant(String path) {

        if (path == null || path.isBlank() || path.length() > MAX_PATH_LENGTH) {
            return false;
        }

        String[] segments = path.split("/");

        for (int i = 0; i < segments.length - 1; i++) {
            if (EXCLUDED_DIRECTORIES.contains(segments[i].toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        String name = segments[segments.length - 1].toLowerCase(Locale.ROOT);

        for (String suffix : GENERATED_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return false;
            }
        }

        return isReadme(name)
                || isConfigFile(name)
                || LANGUAGE_BY_EXTENSION.containsKey(extension(name))
                || isDocumentation(segments, name);
    }

    public Selection select(List<TreeEntry> entries) {

        List<TreeEntry> relevant = entries.stream()
                .filter(entry -> isRelevant(entry.path()))
                .sorted(Comparator
                        .comparingInt((TreeEntry entry) -> rank(entry.path()))
                        .thenComparingInt(entry -> depth(entry.path()))
                        .thenComparing(TreeEntry::path))
                .toList();

        List<TreeEntry> selected = new ArrayList<>();
        long totalBytes = 0;

        for (TreeEntry entry : relevant) {

            if (selected.size() >= properties.getMaxFiles()) {
                break;
            }
            if (entry.size() <= 0 || entry.size() > properties.getMaxFileSizeBytes()) {
                continue; // empty or too large
            }
            if (totalBytes + entry.size() > properties.getMaxTotalSizeBytes()) {
                continue; // would exceed the total budget; a smaller file may still fit
            }

            selected.add(entry);
            totalBytes += entry.size();
        }

        return new Selection(selected, relevant.size());
    }

    /** Language label stored with each file, useful later for chunking in the AI Service. */
    public String languageOf(String path) {

        String name = fileName(path).toLowerCase(Locale.ROOT);
        String language = LANGUAGE_BY_EXTENSION.get(extension(name));

        if (language != null) {
            return language;
        }

        if (name.startsWith("readme") || DOC_EXTENSIONS.contains(extension(name))) {
            return "DOCUMENTATION";
        }

        return "CONFIG";
    }

    // ------------------------------------------------------------------

    /** README, README.md, README.zh-cn.md ... but not readme.png. */
    private boolean isReadme(String name) {
        return name.startsWith("readme")
                && (!name.contains(".") || DOC_EXTENSIONS.contains(extension(name)));
    }

    private boolean isConfigFile(String name) {
        return CONFIG_FILE_NAMES.contains(name)
                || (name.startsWith("application")
                && (name.endsWith(".properties") || name.endsWith(".yml") || name.endsWith(".yaml")))
                || (name.startsWith("requirements") && name.endsWith(".txt"));
    }

    /** Markdown-style docs, but only inside a top-level docs/ or doc/ folder. */
    private boolean isDocumentation(String[] segments, String name) {

        if (segments.length < 2) {
            return false;
        }

        String topLevel = segments[0].toLowerCase(Locale.ROOT);
        String extension = extension(name);

        return (topLevel.equals("docs") || topLevel.equals("doc"))
                && (extension.equals("md") || extension.equals("markdown")
                || extension.equals("rst") || extension.equals("adoc"));
    }

    /** Lower number = downloaded first: README, then config, then source, then docs. */
    private int rank(String path) {

        String name = fileName(path).toLowerCase(Locale.ROOT);

        if (isReadme(name)) {
            return 0;
        }
        if (isConfigFile(name)) {
            return 1;
        }
        if (LANGUAGE_BY_EXTENSION.containsKey(extension(name))) {
            return 2;
        }
        return 3;
    }

    private int depth(String path) {
        int depth = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                depth++;
            }
        }
        return depth;
    }

    private String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }
}
