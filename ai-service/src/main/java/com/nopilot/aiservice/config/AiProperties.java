package com.nopilot.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "nopilot.ai")
public class AiProperties {
    private final RepositoryService repositoryService = new RepositoryService();
    private final Ingestion ingestion = new Ingestion();
    private final Chunking chunking = new Chunking();
    private final Retrieval retrieval = new Retrieval();
    private final Chat chat = new Chat();
    private final Guide guide = new Guide();

    public RepositoryService getRepositoryService() { return repositoryService; }
    public Ingestion getIngestion() { return ingestion; }
    public Chunking getChunking() { return chunking; }
    public Retrieval getRetrieval() { return retrieval; }
    public Chat getChat() { return chat; }
    public Guide getGuide() { return guide; }

    public static class RepositoryService {
        private String baseUrl = "http://localhost:8082";
        private int connectTimeoutSeconds = 5;
        private int readTimeoutSeconds = 60;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String value) { this.baseUrl = value; }
        public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
        public void setConnectTimeoutSeconds(int value) { this.connectTimeoutSeconds = value; }
        public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
        public void setReadTimeoutSeconds(int value) { this.readTimeoutSeconds = value; }
    }

    public static class Ingestion {
        private long maxFileBytes = 102400;
        private int embeddingBatchSize = 16;
        private int workerThreads = 1;
        private int queueCapacity = 10;
        public long getMaxFileBytes() { return maxFileBytes; }
        public void setMaxFileBytes(long value) { this.maxFileBytes = value; }
        public int getEmbeddingBatchSize() { return embeddingBatchSize; }
        public void setEmbeddingBatchSize(int value) { this.embeddingBatchSize = value; }
        public int getWorkerThreads() { return workerThreads; }
        public void setWorkerThreads(int value) { this.workerThreads = value; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int value) { this.queueCapacity = value; }
    }

    public static class Chunking {
        private int maxChars = 1500;
        private int overlapLines = 2;
        private int maxChunksPerFile = 300;
        public int getMaxChars() { return maxChars; }
        public void setMaxChars(int value) { this.maxChars = value; }
        public int getOverlapLines() { return overlapLines; }
        public void setOverlapLines(int value) { this.overlapLines = value; }
        public int getMaxChunksPerFile() { return maxChunksPerFile; }
        public void setMaxChunksPerFile(int value) { this.maxChunksPerFile = value; }
    }

    public static class Retrieval {
        private int defaultLimit = 5;
        private int maxLimit = 20;
        private double similarityThreshold = 0.30;
        private int snippetChars = 400;
        public int getDefaultLimit() { return defaultLimit; }
        public void setDefaultLimit(int value) { this.defaultLimit = value; }
        public int getMaxLimit() { return maxLimit; }
        public void setMaxLimit(int value) { this.maxLimit = value; }
        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double value) { this.similarityThreshold = value; }
        public int getSnippetChars() { return snippetChars; }
        public void setSnippetChars(int value) { this.snippetChars = value; }
    }

    public static class Chat {
        private int retrievalTopK = 8;
        private int maxChunksInPrompt = 5;
        private int maxChunksPerFile = 2;
        private int maxContextChars = 5000;
        private double temperature = 0.1;
        private int maxOutputTokens = 512;
        private int maxQuestionChars = 1000;
        public int getRetrievalTopK() { return retrievalTopK; }
        public void setRetrievalTopK(int value) { this.retrievalTopK = value; }
        public int getMaxChunksInPrompt() { return maxChunksInPrompt; }
        public void setMaxChunksInPrompt(int value) { this.maxChunksInPrompt = value; }
        public int getMaxChunksPerFile() { return maxChunksPerFile; }
        public void setMaxChunksPerFile(int value) { this.maxChunksPerFile = value; }
        public int getMaxContextChars() { return maxContextChars; }
        public void setMaxContextChars(int value) { this.maxContextChars = value; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double value) { this.temperature = value; }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int value) { this.maxOutputTokens = value; }
        public int getMaxQuestionChars() { return maxQuestionChars; }
        public void setMaxQuestionChars(int value) { this.maxQuestionChars = value; }
    }

    public static class Guide {
        private int maxContextChars = 5000;
        private int maxOutputTokens = 700;
        private int maxFilesListed = 40;
        private int maxDependencies = 30;
        public int getMaxContextChars() { return maxContextChars; }
        public void setMaxContextChars(int value) { this.maxContextChars = value; }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int value) { this.maxOutputTokens = value; }
        public int getMaxFilesListed() { return maxFilesListed; }
        public void setMaxFilesListed(int value) { this.maxFilesListed = value; }
        public int getMaxDependencies() { return maxDependencies; }
        public void setMaxDependencies(int value) { this.maxDependencies = value; }
    }
}
