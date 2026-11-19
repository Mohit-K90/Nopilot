package com.nopilot.aiservice.service;

import com.nopilot.aiservice.model.ContextChunk;
import com.nopilot.aiservice.model.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class ContextBuilder {

    public List<ContextChunk> build(
            UUID repositoryId,
            List<RetrievedChunk> retrieved,
            int maxChunks,
            int maxChunksPerFile,
            int maxContextChars) {

        List<RetrievedChunk> candidates = onlyRepository(repositoryId, retrieved);
        candidates.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());

        Set<String> seen = new HashSet<>();
        Map<String, Integer> perFile = new HashMap<>();
        List<ContextChunk> selected = new ArrayList<>();
        int usedChars = 0;

        for (RetrievedChunk chunk : candidates) {
            if (selected.size() >= maxChunks) break;
            String text = chunk.text();
            if (text == null || text.isBlank()) continue;

            String identity = chunk.contentHash() != null
                    ? chunk.contentHash()
                    : chunk.filePath() + ":" + chunk.startLine();
            if (!seen.add(identity)) continue;

            String file = String.valueOf(chunk.filePath());
            if (perFile.getOrDefault(file, 0) >= maxChunksPerFile) continue;
            if (usedChars + text.length() > maxContextChars) continue;

            perFile.merge(file, 1, Integer::sum);
            usedChars += text.length();
            selected.add(new ContextChunk(
                    selected.size() + 1,
                    chunk.filePath(),
                    chunk.language(),
                    chunk.startLine(),
                    chunk.endLine(),
                    chunk.symbol(),
                    chunk.score(),
                    text
            ));
        }
        return selected;
    }

    public List<RetrievedChunk> onlyRepository(UUID repositoryId, List<RetrievedChunk> chunks) {
        List<RetrievedChunk> kept = new ArrayList<>();
        if (chunks == null) return kept;
        for (RetrievedChunk chunk : chunks) {
            if (chunk != null && chunk.belongsTo(repositoryId)) kept.add(chunk);
        }
        return kept;
    }

    public int totalChars(List<ContextChunk> chunks) {
        int total = 0;
        for (ContextChunk chunk : chunks) {
            total += chunk.text().length();
        }
        return total;
    }
}
