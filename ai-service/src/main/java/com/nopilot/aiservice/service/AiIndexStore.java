package com.nopilot.aiservice.service;

import com.nopilot.aiservice.model.CodeChunk;
import com.nopilot.aiservice.model.RetrievedChunk;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Owns repository indexing metadata in PostgreSQL and delegates all embedding/vector
 * operations to Spring AI's PGVectorStore. Repository and owner metadata are applied
 * to every document and every search/delete filter.
 */
@Repository
public class AiIndexStore {
    private final JdbcTemplate jdbc;
    private final VectorStore vectorStore;

    public AiIndexStore(JdbcTemplate jdbc, VectorStore vectorStore) {
        this.jdbc = jdbc;
        this.vectorStore = vectorStore;
    }

    @Transactional
    public void begin(UUID repositoryId, String email) {
        deleteVectors(repositoryId, email);
        jdbc.update("""
            INSERT INTO ai_repository_indexes(repository_id,user_email,status,file_count,chunk_count,updated_at)
            VALUES (?,?,'INGESTING',0,0,CURRENT_TIMESTAMP)
            ON CONFLICT(repository_id) DO UPDATE SET user_email=EXCLUDED.user_email,
              status='INGESTING',file_count=0,chunk_count=0,failure_reason=NULL,
              indexed_at=NULL,updated_at=CURRENT_TIMESTAMP
            """, repositoryId, email);
    }

    public void saveBatch(UUID repositoryId, String email, List<CodeChunk> chunks) {
        List<Document> documents = chunks.stream().map(chunk -> {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("repositoryId", repositoryId.toString());
            metadata.put("ownerEmail", email);
            metadata.put("filePath", chunk.filePath());
            metadata.put("language", chunk.language());
            metadata.put("chunkIndex", chunk.chunkIndex());
            metadata.put("startLine", chunk.startLine());
            metadata.put("endLine", chunk.endLine());
            metadata.put("contentHash", chunk.contentHash());
            if (chunk.symbol() != null && !chunk.symbol().isBlank()) {
                metadata.put("symbol", chunk.symbol());
            }
            return new Document(chunk.id(repositoryId), chunk.content(), metadata);
        }).toList();
        vectorStore.add(documents);
    }

    @Transactional
    public void complete(UUID id, String email, int files, int chunks) {
        jdbc.update("""
            UPDATE ai_repository_indexes SET user_email=?,status='READY',file_count=?,chunk_count=?,
              failure_reason=NULL,indexed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
            WHERE repository_id=?
            """, email, files, chunks, id);
    }

    @Transactional
    public void fail(UUID id, String email, String reason) {
        deleteVectors(id, email);
        jdbc.update("""
            UPDATE ai_repository_indexes SET status='FAILED',failure_reason=?,file_count=0,
              chunk_count=0,updated_at=CURRENT_TIMESTAMP WHERE repository_id=? AND user_email=?
            """, truncate(reason, 1000), id, email);
    }

    public Map<String, Object> status(UUID id, String email) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT repository_id,status,file_count,chunk_count,failure_reason,indexed_at
            FROM ai_repository_indexes WHERE repository_id=? AND user_email=?
            """, id, email);
        if (rows.isEmpty()) {
            throw new NoSuchElementException("AI index not found for this repository");
        }
        return rows.get(0);
    }

    public List<RetrievedChunk> search(UUID id, String email, String query, int limit, double threshold) {
        FilterExpressionBuilder filter = new FilterExpressionBuilder();
        Filter.Expression repositoryFilter = filter.and(
                filter.eq("repositoryId", id.toString()),
                filter.eq("ownerEmail", email)
        ).build();

        return vectorStore.similaritySearch(SearchRequest.builder()
                        .query(query)
                        .topK(limit)
                        .similarityThreshold(threshold)
                        .filterExpression(repositoryFilter)
                        .build())
                .stream()
                .map(document -> new RetrievedChunk(
                        document.getId(),
                        document.getScore() == null ? 0.0 : document.getScore(),
                        document.getMetadata(),
                        document.getText()))
                .toList();
    }

    private void deleteVectors(UUID repositoryId, String email) {
        FilterExpressionBuilder filter = new FilterExpressionBuilder();
        Filter.Expression expression = filter.and(
                filter.eq("repositoryId", repositoryId.toString()),
                filter.eq("ownerEmail", email)
        ).build();
        vectorStore.delete(expression);
    }

    private String truncate(String value, int max) {
        if (value == null) return "Unknown indexing failure";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
