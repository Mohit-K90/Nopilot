package com.nopilot.aiservice.service;

import com.nopilot.aiservice.config.AiProperties;
import com.nopilot.aiservice.model.CodeChunk;
import com.nopilot.aiservice.util.FileHasher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChunkingService {
    public static final String VERSION = "1";

    private static final List<Pattern> SYMBOL_PATTERNS = List.of(
            Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Za-z_][\\w]*)"),
            Pattern.compile("^\\s*(?:async\\s+)?def\\s+([A-Za-z_]\\w*)"),
            Pattern.compile("\\bfunction\\s*\\*?\\s*([A-Za-z_$][\\w$]*)"),
            Pattern.compile("^\\s*(?:export\\s+)?(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)")
    );

    private final int maxChars;
    private final int overlapLines;
    private final int maxChunksPerFile;

    public ChunkingService(AiProperties properties) {
        this.maxChars = properties.getChunking().getMaxChars();
        this.overlapLines = properties.getChunking().getOverlapLines();
        this.maxChunksPerFile = properties.getChunking().getMaxChunksPerFile();
    }

    public String settingsFingerprint() {
        return VERSION + ":" + maxChars + ":" + overlapLines;
    }

    public List<CodeChunk> chunk(String path, String language, String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int lineCount = lines.length;
        if (lineCount > 0 && lines[lineCount - 1].isEmpty()) {
            lineCount--;
        }
        if (lineCount <= 0) {
            return List.of();
        }

        List<CodeChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < lineCount && chunks.size() < maxChunksPerFile) {
            int end = start;
            int chars = 0;
            while (end < lineCount) {
                int nextLength = lines[end].length() + 1;
                if (end > start && chars + nextLength > maxChars) {
                    break;
                }
                chars += nextLength;
                end++;
                if (chars >= maxChars) {
                    break;
                }
            }

            StringBuilder text = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (i > start) text.append('\n');
                text.append(lines[i]);
            }
            String chunkText = text.toString();
            if (!chunkText.isBlank()) {
                chunks.add(new CodeChunk(
                        path,
                        language,
                        chunks.size(),
                        start + 1,
                        end,
                        extractSymbol(chunkText),
                        chunkText,
                        FileHasher.sha256Hex(chunkText)
                ));
            }

            if (end >= lineCount) {
                break;
            }
            int overlap = Math.min(Math.max(overlapLines, 0), Math.max(end - start - 1, 0));
            start = Math.max(start + 1, end - overlap);
        }
        return chunks;
    }

    private String extractSymbol(String content) {
        for (String line : content.split("\n")) {
            for (Pattern pattern : SYMBOL_PATTERNS) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        return null;
    }
}
