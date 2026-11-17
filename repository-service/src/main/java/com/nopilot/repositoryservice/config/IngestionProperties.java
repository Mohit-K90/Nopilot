package com.nopilot.repositoryservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Limits that keep ingestion of large repositories bounded.
 * Bound from the "nopilot.ingestion" section of application.yaml.
 */
@Component
@ConfigurationProperties(prefix = "nopilot.ingestion")
public class IngestionProperties {

    /** Maximum number of files kept per repository. */
    private int maxFiles = 200;

    /** Files larger than this (bytes) are skipped. Default 100 KB. */
    private long maxFileSizeBytes = 100 * 1024;

    /** Maximum combined size (bytes) of all kept files. Default 5 MB. */
    private long maxTotalSizeBytes = 5L * 1024 * 1024;

    public int getMaxFiles() {
        return maxFiles;
    }

    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public long getMaxTotalSizeBytes() {
        return maxTotalSizeBytes;
    }

    public void setMaxTotalSizeBytes(long maxTotalSizeBytes) {
        this.maxTotalSizeBytes = maxTotalSizeBytes;
    }
}
